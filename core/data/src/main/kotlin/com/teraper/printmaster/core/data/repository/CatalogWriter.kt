package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.entity.ClientItemEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartValueEntity
import com.teraper.printmaster.core.database.entity.ClientItemValueEntity
import com.teraper.printmaster.core.database.entity.ItemEntity
import com.teraper.printmaster.core.database.entity.ItemFieldEntity
import com.teraper.printmaster.core.database.entity.ItemFitEntity
import com.teraper.printmaster.core.database.entity.ItemTypeEntity
import com.teraper.printmaster.core.database.entity.ItemValueEntity
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemPart
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemTitles
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.core.model.Money
import javax.inject.Inject

/** The whole catalog put together: types with fields, items with values and links. */
internal class CatalogSnapshot(val types: Map<Long, ItemType>, val items: Map<Long, CatalogItem>) {

    /** [values] are the item's own (one of a kind), [partValues] by part id (parts described by values). */
    fun clientItem(
        row: ClientItemEntity,
        parts: List<ClientItemPartEntity>,
        values: List<ClientItemValueEntity> = emptyList(),
        partValues: List<ClientItemPartValueEntity> = emptyList(),
    ): ClientItem? {
        val type = types[row.typeId] ?: return null
        val partValuesBy = partValues.groupBy({ it.partId }, { it.fieldId to it.value })
        return ClientItem(
            id = row.id,
            clientId = row.clientId,
            type = type,
            item = row.itemId?.let(items::get),
            name = row.name,
            values = values.filter { it.clientItemId == row.id }.associate { it.fieldId to it.value },
            place = row.place,
            note = row.note,
            serialNumber = row.serialNumber.orEmpty(),
            parts = parts.mapNotNull { part ->
                val partType = types[part.typeId] ?: return@mapNotNull null
                ClientItemPart(
                    id = part.id,
                    type = partType,
                    item = part.itemId?.let(items::get),
                    values = partValuesBy[part.id].orEmpty().toMap(),
                    serialNumber = part.serialNumber.orEmpty(),
                    note = part.note,
                )
            }.sortedWith(compareBy({ CatalogNames.key(it.type.name) }, { it.item == null }, { CatalogNames.codeKey(it.title) })),
        )
    }

    /** The client's item as stored, put together. */
    suspend fun clientItem(dao: CatalogDao, row: ClientItemEntity): ClientItem? =
        clientItem(row, dao.getClientItemParts(row.id), dao.getClientItemValues(row.id), dao.getClientPartValues(row.id))

    /** The item of [typeId] whose title (or bare name) is [title], other than [excludeId]. */
    fun find(typeId: Long, title: String, excludeId: Long = 0): CatalogItem? {
        val key = CatalogNames.codeKey(title)
        return items.values.filter { it.typeId == typeId && it.id != excludeId }.let { same ->
            same.firstOrNull { CatalogNames.codeKey(it.title) == key } ?: same.firstOrNull { CatalogNames.codeKey(it.name) == key }
        }
    }

    companion object {
        fun of(
            types: List<ItemTypeEntity>,
            fields: List<ItemFieldEntity>,
            items: List<ItemEntity>,
            values: List<ItemValueEntity>,
            fits: List<ItemFitEntity>,
        ): CatalogSnapshot {
            val fieldsByType = fields.groupBy { it.typeId }
            val typeMap = types.associate { t ->
                t.id to ItemType(
                    id = t.id,
                    name = t.name,
                    parentId = t.parentId,
                    hasSerial = t.hasSerial,
                    perClient = t.perClient,
                    hasPhotos = t.hasPhotos,
                    fields = fieldsByType[t.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).map { f ->
                        ItemField(f.id, f.typeId, f.name, f.kind, f.options.lines().filter { it.isNotBlank() }, f.inTitle)
                    },
                )
            }
            val valuesByItem = values.groupBy({ it.itemId }, { it.fieldId to it.value })
            val bare = items.mapNotNull { row ->
                val type = typeMap[row.typeId] ?: return@mapNotNull null
                row.id to CatalogItem(
                    row.id, type, row.name, valuesByItem[row.id].orEmpty().toMap(),
                    barcode = row.barcode.orEmpty(), price = Money(row.priceMinor), cost = Money(row.costMinor),
                )
            }.toMap()
            val refs = bare.mapValues { it.value.ref() }
            val byTitle = compareBy<ItemRef> { CatalogNames.codeKey(it.title) }
            val into = fits.groupBy({ it.itemId }, { it.intoId })
            val parts = fits.groupBy({ it.intoId }, { it.itemId })
            return CatalogSnapshot(
                typeMap,
                bare.mapValues { (id, item) ->
                    item.copy(
                        fitsInto = into[id].orEmpty().mapNotNull(refs::get).sortedWith(byTitle),
                        parts = parts[id].orEmpty().mapNotNull(refs::get).sortedWith(compareBy<ItemRef> { it.typeName }.then(byTitle)),
                    )
                },
            )
        }
    }
}

/**
 * Find-or-create for catalog names, shared by the catalog, client-item forms and sync.
 * Names are matched ignoring case and spacing, so typing "cf 283a" reuses "CF283A".
 * Call inside a transaction.
 */
internal class CatalogWriter @Inject constructor(private val dao: CatalogDao) {

    suspend fun snapshot(): CatalogSnapshot =
        CatalogSnapshot.of(dao.getTypes(), dao.getFields(), dao.getItems(), dao.getValues(), dao.getFits())

    /** The type named [name], made under [parentId] (with the given switches) when missing. */
    suspend fun typeId(name: String, parentId: Long? = null, hasSerial: Boolean = true, perClient: Boolean = false, hasPhotos: Boolean = true): Long {
        val clean = CatalogNames.clean(name)
        return dao.getTypes().firstOrNull { CatalogNames.key(it.name) == CatalogNames.key(clean) }?.id
            ?: dao.insertType(ItemTypeEntity(name = clean, parentId = parentId, hasSerial = hasSerial, perClient = perClient, hasPhotos = hasPhotos))
    }

    /** The field named [name] of the type, made (as text) when missing. */
    suspend fun fieldId(typeId: Long, name: String, kind: FieldKind = FieldKind.TEXT, options: List<String> = emptyList(), inTitle: Boolean = false): Long {
        val clean = CatalogNames.clean(name)
        val fields = dao.getFields().filter { it.typeId == typeId }
        return fields.firstOrNull { CatalogNames.key(it.name) == CatalogNames.key(clean) }?.id
            ?: dao.insertField(
                ItemFieldEntity(typeId = typeId, name = clean, kind = kind, options = options.joinToString("\n"), inTitle = inTitle, position = fields.size),
            )
    }

    /** The item of [typeId] called [title], made with [values] when missing. */
    suspend fun findOrCreateItem(typeId: Long, title: String, values: Map<Long, String> = emptyMap(), name: String = title): Long {
        snapshot().find(typeId, title)?.let { return it.id }
        val id = dao.insertItem(ItemEntity(typeId = typeId, name = CatalogNames.clean(name)))
        setValues(id, values)
        return id
    }

    suspend fun setValues(itemId: Long, values: Map<Long, String>) {
        val clean = values.mapValues { CatalogNames.clean(it.value) }.filterValues { it.isNotEmpty() }
        dao.deleteValuesExcept(itemId, clean.keys.toList())
        dao.upsertValues(clean.map { (field, value) -> ItemValueEntity(itemId, field, value) })
    }

    /** The ids of [links], making new items for the ones typed by name; by [LinkDraft.key]. */
    private suspend fun resolve(links: List<LinkDraft>): Map<String, Long> = links.associate { link ->
        link.key to (link.id.takeIf { it != 0L && dao.getItem(it) != null } ?: findOrCreateItem(link.typeId, link.title))
    }

    /** Puts [parts] into [intoId]. With [exact], parts missing from the list are taken out. */
    suspend fun linkParts(intoId: Long, parts: List<LinkDraft>, exact: Boolean): Map<String, Long> {
        val ids = resolve(parts)
        dao.linkFits(ids.values.map { ItemFitEntity(itemId = it, intoId = intoId) })
        if (exact) dao.unlinkPartsExcept(intoId, ids.values.toList())
        return ids
    }

    /** Puts [itemId] into [into]. With [exact], others are taken out. */
    suspend fun linkInto(itemId: Long, into: List<LinkDraft>, exact: Boolean) {
        val ids = resolve(into)
        dao.linkFits(ids.values.map { ItemFitEntity(itemId = itemId, intoId = it) })
        if (exact) dao.unlinkIntoExcept(itemId, ids.values.toList())
    }

    /** The title an item would have: values of title fields, then the name. */
    fun title(type: ItemType, name: String, values: Map<Long, String>): String = ItemTitles.title(type, name, values)
}
