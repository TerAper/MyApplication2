package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.entity.ClientItemEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartValueEntity
import com.teraper.printmaster.core.database.entity.ClientItemValueEntity
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
                dao.insertType(ItemTypeEntity(name = clean.name, parentId = parentId, hasSerial = clean.hasSerial, perClient = clean.perClient, hasPhotos = clean.hasPhotos))
            } else {
                dao.updateType(ItemTypeEntity(id = clean.id, name = clean.name, parentId = parentId, hasSerial = clean.hasSerial, perClient = clean.perClient, hasPhotos = clean.hasPhotos))
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
            val typeId = writer.typeId(template.name, parentId, template.hasSerial, template.perClient, template.hasPhotos)
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
            // One barcode is one product: scanning it must find a single item.
            val barcodeKey = SerialNumbers.key(clean.barcode).ifEmpty { null }
            snapshot.items.values.firstOrNull { it.id != clean.id && barcodeKey != null && SerialNumbers.key(it.barcode) == barcodeKey }?.let {
                return@withTransaction SaveItemResult.BarcodeTaken(it.title)
            }
            val row = ItemEntity(
                id = clean.id,
                typeId = type.id,
                name = clean.name,
                barcode = clean.barcode.ifEmpty { null },
                barcodeKey = barcodeKey,
                priceMinor = clean.price.minor,
                costMinor = clean.cost.minor,
                minStock = clean.keepAtLeast,
            )
            val itemId = if (clean.isNew) {
                dao.insertItem(row)
            } else {
                dao.getItem(clean.id) ?: return@withTransaction SaveItemResult.Invalid(errors)
                dao.updateItem(row)
                clean.id
            }
            writer.setValues(itemId, values)
            // Only items of the parent type can hold it, only items of its part types go into it.
            writer.linkInto(itemId, clean.fitsInto.filter { it.typeId == type.parentId }, exact = true)
            writer.linkParts(itemId, clean.parts.filter { link -> snapshot.types[link.typeId]?.parentId == type.id }, exact = true)
            SaveItemResult.Saved(itemId)
        }
    }

    override suspend fun findByBarcode(code: String): CatalogItem? {
        val key = SerialNumbers.key(code).ifEmpty { return null }
        return writer.snapshot().items.values.firstOrNull { SerialNumbers.key(it.barcode) == key }
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

    override fun observeClientItems(clientId: Long): Flow<List<ClientItem>> = combine(
        dao.observeSnapshot(),
        dao.observeClientItems(clientId),
        dao.observeClientItemParts(clientId),
        dao.observeClientItemValues(clientId),
        dao.observeClientPartValues(clientId),
    ) { snapshot, rows, parts, values, partValues ->
        val partsBy = parts.groupBy { it.clientItemId }
        rows.mapNotNull { snapshot.clientItem(it, partsBy[it.id].orEmpty(), values, partValues) }
    }

    override suspend fun getClientItem(id: Long): ClientItem? {
        val row = dao.getClientItem(id) ?: return null
        return writer.snapshot().clientItem(dao, row)
    }

    override suspend fun saveClientItem(draft: ClientItemDraft): SaveClientItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveClientItemResult.Invalid(errors)
        val model = draft.item.normalized()
        val place = CatalogNames.clean(draft.place)
        val note = draft.note.trim()

        return db.withTransaction {
            val snapshot = writer.snapshot()
            val type = snapshot.types[model.typeId] ?: return@withTransaction SaveClientItemResult.Invalid(errors)
            val values = model.values.filterKeys { id -> type.fields.any { it.id == id } }
            val serial = SerialNumbers.clean(draft.serialNumber).takeIf { type.hasSerial }?.ifEmpty { null }
            val serialKey = serial?.let(SerialNumbers::key)?.ifEmpty { null }
            // One of a kind (a child): its name and values are its own, not a catalog model.
            val itemId = when {
                type.perClient -> null
                model.isNew -> writer.findOrCreateItem(type.id, writer.title(type, model.name, values), values, model.name)
                else -> model.id
            }
            val partTypes = snapshot.types.values.filter { it.parentId == type.id }.associateBy { it.id }
            val parts = draft.parts.filter { it.typeId in partTypes }
            // Add-only: this form never takes parts out of the shared catalog model.
            val linked = if (itemId != null) {
                writer.linkParts(itemId, parts.filterNot { it.isSpec }.map { it.link() }, exact = false)
            } else {
                parts.filterNot { it.isSpec }.associate { it.link().key to (it.itemId.takeIf { id -> id != 0L } ?: writer.findOrCreateItem(it.typeId, it.title)) }
            }

            val row = ClientItemEntity(
                id = if (draft.isNew) 0 else draft.id,
                clientId = draft.clientId,
                typeId = type.id,
                itemId = itemId,
                name = if (type.perClient) model.name else "",
                place = place,
                note = note,
                // Keep the shared id, so masters' phones see the same item, not a new one.
                syncId = if (draft.isNew) null else dao.getClientItem(draft.id)?.syncId,
                serialNumber = serial,
                serialKey = serialKey,
            )
            val clientItemId = if (draft.isNew) dao.insertClientItem(row) else draft.id.also { dao.updateClientItem(row) }
            dao.deleteClientItemValues(clientItemId)
            if (type.perClient) dao.insertClientItemValues(values.map { (field, value) -> ClientItemValueEntity(clientItemId, field, value) })

            // Rows still listed keep their id, so work and photos stay linked to them.
            val existing = dao.getClientItemParts(clientItemId).map { it.id }.toSet()
            val kept = parts.filter { it.id in existing }.map { it.id }
            dao.deleteClientItemPartsExcept(clientItemId, kept)
            for (part in parts) {
                val partType = partTypes.getValue(part.typeId)
                val partRow = ClientItemPartEntity(
                    id = part.id.takeIf { it in existing } ?: 0,
                    clientItemId = clientItemId,
                    typeId = part.typeId,
                    itemId = if (part.isSpec) null else linked[part.link().key],
                    note = part.note.trim(),
                    serialNumber = SerialNumbers.clean(part.serialNumber).takeIf { partType.hasSerial }?.ifEmpty { null },
                )
                val partId = if (partRow.id == 0L) dao.insertClientItemPart(partRow) else partRow.id.also { dao.updateClientItemPart(partRow) }
                dao.deleteClientPartValues(partId)
                if (part.isSpec) {
                    val partValues = part.values.filterKeys { id -> partType.fields.any { it.id == id } }
                        .mapValues { CatalogNames.clean(it.value) }.filterValues { it.isNotEmpty() }
                    dao.insertClientPartValues(partValues.map { (field, value) -> ClientItemPartValueEntity(partId, field, value) })
                }
            }
            SaveClientItemResult.Saved(clientItemId)
        }
    }

    override suspend fun findBySerial(serial: String): List<SerialMatch> {
        val key = SerialNumbers.key(serial).ifEmpty { return emptyList() }
        val clientDao = db.clientDao()
        val snapshot = writer.snapshot()
        return dao.findClientItemsBySerial(key, partial = true).mapNotNull { row ->
            val item = snapshot.clientItem(dao, row) ?: return@mapNotNull null
            SerialMatch(item, clientDao.getClient(row.clientId)?.name.orEmpty(), exact = row.serialKey == key)
        }
    }

    override suspend fun deleteClientItem(id: Long): Boolean = dao.deleteClientItem(id) > 0
}
