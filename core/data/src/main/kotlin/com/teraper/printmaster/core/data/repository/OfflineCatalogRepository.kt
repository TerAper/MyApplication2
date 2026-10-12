package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.entity.ClientItemEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartEntity
import com.teraper.printmaster.core.database.entity.ItemEntity
import com.teraper.printmaster.core.database.entity.ItemFieldEntity
import com.teraper.printmaster.core.database.entity.ItemTypeEntity
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.ItemTypes
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.model.SerialNumbers
import com.teraper.printmaster.core.model.TypeTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The catalog as it changes: every table that makes it up, put together. */
internal fun CatalogDao.observeSnapshot(): Flow<CatalogSnapshot> =
    combine(observeTypes(), observeFields(), observeItems(), observeValues(), observeFits()) { types, fields, items, values, fits ->
        CatalogSnapshot.of(types, fields, items, values, fits)
    }

internal class OfflineCatalogRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: CatalogDao,
    private val writer: CatalogWriter,
) : CatalogRepository {

    override fun observeTypes(): Flow<List<ItemType>> =
        dao.observeSnapshot().map { snapshot -> ItemTypes.tree(snapshot.types.values.toList()).map { it.type } }

    override suspend fun saveType(draft: ItemTypeDraft): SaveTypeResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveTypeResult.Invalid(errors)
        val clean = draft.normalized()
        return db.withTransaction {
            val types = dao.getTypes()
            if (types.any { it.id != clean.id && CatalogNames.key(it.name) == CatalogNames.key(clean.name) }) {
                return@withTransaction SaveTypeResult.NameTaken
            }
            // A type can't go into itself or into one of its own parts.
            val below = if (clean.isNew) emptySet() else ItemTypes.selfAndBelow(types.map { ItemType(it.id, it.name, it.parentId) }, clean.id)
            val parentId = clean.parentId?.takeIf { id -> id !in below && types.any { it.id == id } }
            val typeId = if (clean.isNew) {
                dao.insertType(ItemTypeEntity(name = clean.name, parentId = parentId))
            } else {
                dao.updateType(ItemTypeEntity(id = clean.id, name = clean.name, parentId = parentId))
                clean.id
            }
            val existing = dao.getFields().filter { it.typeId == typeId }.associateBy { it.id }
            val kept = clean.fields.mapIndexed { index, field ->
                val row = ItemFieldEntity(
                    id = field.id.takeIf { it in existing } ?: 0,
                    typeId = typeId,
                    name = field.name,
                    kind = field.kind,
                    options = field.optionList().joinToString("\n"),
                    inTitle = field.inTitle,
                    position = index,
                )
                if (row.id == 0L) dao.insertField(row) else row.id.also { dao.updateField(row) }
            }
            dao.deleteFieldsExcept(typeId, kept)
            SaveTypeResult.Saved(typeId)
        }
    }

    override suspend fun deleteType(id: Long): DeleteResult = db.withTransaction {
        when {
            dao.getTypes().none { it.id == id } -> DeleteResult.NOT_FOUND
            dao.countItemsOfType(id) > 0 -> DeleteResult.IN_USE
            else -> {
                dao.deleteType(id)
                DeleteResult.DELETED
            }
        }
    }

    override suspend fun applyTemplate(types: List<TypeTemplate>): Int = db.withTransaction {
        var added = 0
        for (template in types) {
            val before = dao.getTypes().size
            val parentId = template.parentName?.let { name ->
                dao.getTypes().firstOrNull { CatalogNames.key(it.name) == CatalogNames.key(name) }?.id
            }
            val typeId = writer.typeId(template.name, parentId)
            if (dao.getTypes().size > before) added++
            template.fields.filter { it.name.isNotBlank() }.forEach { field ->
                writer.fieldId(typeId, field.name, field.kind, field.optionList(), field.inTitle)
            }
        }
        added
    }

    override fun observeCatalog(): Flow<List<CatalogEntry>> = combine(dao.observeSnapshot(), dao.observeOwnedCounts()) { snapshot, counts ->
        val countById = counts.associate { it.id to it.count }
        val typeOrder = ItemTypes.tree(snapshot.types.values.toList()).mapIndexed { index, it -> it.type.id to index }.toMap()
        snapshot.items.values
            .sortedWith(compareBy<CatalogItem>({ typeOrder[it.typeId] ?: Int.MAX_VALUE }, { CatalogNames.key(it.title) }))
            .map { CatalogEntry(it, countById[it.id] ?: 0) }
    }

    override fun observeItem(id: Long): Flow<CatalogItem?> = dao.observeSnapshot().map { it.items[id] }

    override fun observeOwners(itemId: Long): Flow<List<ItemOwner>> =
        dao.observeOwners(itemId).map { rows -> rows.map { ItemOwner(it.clientId, it.clientName, it.place) } }

    override suspend fun saveItem(draft: ItemDraft): SaveItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveItemResult.Invalid(errors)
        val clean = draft.normalized()
        return db.withTransaction {
            val snapshot = writer.snapshot()
            val type = snapshot.types[clean.typeId] ?: return@withTransaction SaveItemResult.Invalid(errors)
            val values = clean.values.filterKeys { id -> type.fields.any { it.id == id } }
            if (snapshot.find(type.id, writer.title(type, clean.name, values), excludeId = clean.id) != null) {
                return@withTransaction SaveItemResult.NameTaken
            }
            val itemId = if (clean.isNew) {
                dao.insertItem(ItemEntity(typeId = type.id, name = clean.name))
            } else {
                dao.getItem(clean.id) ?: return@withTransaction SaveItemResult.Invalid(errors)
                dao.updateItem(ItemEntity(id = clean.id, typeId = type.id, name = clean.name))
                clean.id
            }
            writer.setValues(itemId, values)
            // Only items of the parent type can hold it, only items of its part types go into it.
            writer.linkInto(itemId, clean.fitsInto.filter { it.typeId == type.parentId }, exact = true)
            writer.linkParts(itemId, clean.parts.filter { link -> snapshot.types[link.typeId]?.parentId == type.id }, exact = true)
            SaveItemResult.Saved(itemId)
        }
    }

    override suspend fun deleteItem(id: Long): DeleteResult = db.withTransaction {
        when {
            dao.getItem(id) == null -> DeleteResult.NOT_FOUND
            dao.countUses(id) > 0 -> DeleteResult.IN_USE
            else -> {
                dao.deleteItem(id)
                DeleteResult.DELETED
            }
        }
    }
}

internal class OfflineClientItemsRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: CatalogDao,
    private val writer: CatalogWriter,
) : ClientItemsRepository {

    override fun observeClientItems(clientId: Long): Flow<List<ClientItem>> =
        combine(dao.observeSnapshot(), dao.observeClientItems(clientId), dao.observeClientItemParts(clientId)) { snapshot, rows, parts ->
            val partsBy = parts.groupBy { it.clientItemId }
            rows.mapNotNull { snapshot.clientItem(it, partsBy[it.id].orEmpty()) }
        }

    override suspend fun getClientItem(id: Long): ClientItem? {
        val row = dao.getClientItem(id) ?: return null
        return writer.snapshot().clientItem(row, dao.getClientItemParts(id))
    }

    override suspend fun saveClientItem(draft: ClientItemDraft): SaveClientItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveClientItemResult.Invalid(errors)
        val model = draft.item.normalized()
        val place = CatalogNames.clean(draft.place)
        val note = draft.note.trim()
        val serial = SerialNumbers.clean(draft.serialNumber).ifEmpty { null }
        val serialKey = serial?.let(SerialNumbers::key)?.ifEmpty { null }

        return db.withTransaction {
            val snapshot = writer.snapshot()
            val type = snapshot.types[model.typeId] ?: return@withTransaction SaveClientItemResult.Invalid(errors)
            val itemId = if (model.isNew) {
                val values = model.values.filterKeys { id -> type.fields.any { it.id == id } }
                writer.findOrCreateItem(type.id, writer.title(type, model.name, values), values, model.name)
            } else {
                model.id
            }
            // Add-only: this form never takes parts out of the shared catalog model.
            val partIds = writer.linkParts(itemId, model.parts.filter { link -> snapshot.types[link.typeId]?.parentId == type.id }, exact = false)
            val chosenIds = model.parts.filter { it.key in draft.selectedParts }.mapNotNull { partIds[it.key] }

            val clientItemId = if (draft.isNew) {
                dao.insertClientItem(
                    ClientItemEntity(clientId = draft.clientId, itemId = itemId, place = place, note = note, serialNumber = serial, serialKey = serialKey),
                )
            } else {
                // Keep the shared id, so masters' phones see the same item, not a new one.
                val syncId = dao.getClientItem(draft.id)?.syncId
                dao.updateClientItem(
                    ClientItemEntity(
                        id = draft.id, clientId = draft.clientId, itemId = itemId, place = place, note = note, syncId = syncId,
                        serialNumber = serial, serialKey = serialKey,
                    ),
                )
                draft.id
            }

            // Rows for parts still chosen keep their id, so work stays linked to them.
            val existing = dao.getClientItemParts(clientItemId)
            val kept = existing.filter { it.itemId in chosenIds }
            dao.deleteClientItemPartsExcept(clientItemId, kept.map { it.id })
            val keptItemIds = kept.mapTo(mutableSetOf()) { it.itemId }
            dao.insertClientItemParts(
                chosenIds.distinct().filterNot { it in keptItemIds }.map { ClientItemPartEntity(clientItemId = clientItemId, itemId = it) },
            )
            SaveClientItemResult.Saved(clientItemId)
        }
    }

    override suspend fun findBySerial(serial: String): List<SerialMatch> {
        val key = SerialNumbers.key(serial).ifEmpty { return emptyList() }
        val clientDao = db.clientDao()
        val snapshot = writer.snapshot()
        return dao.findClientItemsBySerial(key, partial = true).mapNotNull { row ->
            val item = snapshot.clientItem(row, dao.getClientItemParts(row.id)) ?: return@mapNotNull null
            SerialMatch(item, clientDao.getClient(row.clientId)?.name.orEmpty(), exact = row.serialKey == key)
        }
    }

    override suspend fun deleteClientItem(id: Long): Boolean = dao.deleteClientItem(id) > 0
}
