package com.teraper.printmaster.core.model

/** A field as typed in the type form. [id] 0 = new. Choices are one per line. */
data class FieldDraft(
    val id: Long = 0,
    val name: String = "",
    val kind: FieldKind = FieldKind.TEXT,
    val options: String = "",
    val inTitle: Boolean = false,
) {
    fun optionList(): List<String> = if (kind == FieldKind.CHOICE) CatalogNames.splitList(options) else emptyList()

    companion object {
        fun from(field: ItemField) = FieldDraft(field.id, field.name, field.kind, field.options.joinToString("\n"), field.inTitle)
    }
}

/** The type form. [id] 0 = new. Fields left with no name are dropped on save. */
data class ItemTypeDraft(
    val id: Long = 0,
    val name: String = "",
    val parentId: Long? = null,
    val fields: List<FieldDraft> = emptyList(),
    val hasSerial: Boolean = true,
    val perClient: Boolean = false,
    val hasPhotos: Boolean = true,
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): Set<ItemTypeError> = buildSet {
        if (name.isBlank()) add(ItemTypeError.NAME_REQUIRED)
        if (fields.any { it.kind == FieldKind.CHOICE && it.name.isNotBlank() && it.optionList().isEmpty() }) add(ItemTypeError.CHOICES_REQUIRED)
    }

    /** Trimmed names; nameless and repeated fields dropped. */
    fun normalized(): ItemTypeDraft = copy(
        name = CatalogNames.clean(name),
        fields = fields
            .map { it.copy(name = CatalogNames.clean(it.name), options = it.optionList().joinToString("\n")) }
            .filter { it.name.isNotEmpty() }
            .distinctBy { CatalogNames.key(it.name) },
    )

    companion object {
        fun from(type: ItemType) = ItemTypeDraft(type.id, type.name, type.parentId, type.fields.map(FieldDraft::from), type.hasSerial, type.perClient, type.hasPhotos)
    }
}

enum class ItemTypeError { NAME_REQUIRED, CHOICES_REQUIRED }

/** A ready set of types (a business template), or a part of an AI suggestion. */
data class TypeTemplate(
    val name: String,
    val parentName: String? = null,
    val fields: List<FieldDraft> = emptyList(),
    val hasSerial: Boolean = true,
    val perClient: Boolean = false,
    val hasPhotos: Boolean = true,
)

data class ItemTemplate(val key: String, val name: String, val types: List<TypeTemplate>)

/**
 * A link to another catalog item in a form: an existing one ([id]) or a new one typed by name.
 * [title] is what's shown and matched; for a new item it is its name.
 */
data class LinkDraft(val id: Long = 0, val typeId: Long, val title: String) {
    val key: String get() = keyOf(typeId, title)

    companion object {
        fun keyOf(typeId: Long, title: String) = "$typeId:${CatalogNames.codeKey(title)}"

        fun from(ref: ItemRef) = LinkDraft(ref.id, ref.typeId, ref.title)
    }
}

/** A catalog item as typed in a form. [id] 0 = not in the catalog yet. */
data class ItemDraft(
    val id: Long = 0,
    val typeId: Long = 0,
    val name: String = "",
    val values: Map<Long, String> = emptyMap(),
    val fitsInto: List<LinkDraft> = emptyList(),
    val parts: List<LinkDraft> = emptyList(),
    val barcode: String = "",
    /** Whole dram as typed; empty = not sold as goods. */
    val priceDigits: String = "",
    val costDigits: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val price: Money get() = Money.ofDram(priceDigits.toLongOrNull() ?: 0L)
    val cost: Money get() = Money.ofDram(costDigits.toLongOrNull() ?: 0L)

    fun withPrice(text: String) = copy(priceDigits = PriceItemDraft.amountDigits(text))

    fun withCost(text: String) = copy(costDigits = PriceItemDraft.amountDigits(text))

    fun validate(): Set<ItemDraftError> = buildSet {
        if (typeId == 0L) add(ItemDraftError.TYPE_REQUIRED)
        if (name.isBlank()) add(ItemDraftError.NAME_REQUIRED)
    }

    fun title(type: ItemType?): String = type?.let { ItemTitles.title(it, name, values) } ?: CatalogNames.clean(name)

    fun withValue(fieldId: Long, value: String) =
        copy(values = if (value.isBlank()) values - fieldId else values + (fieldId to value))

    /** Trimmed name and values; empty and repeated links dropped. */
    fun normalized(): ItemDraft = copy(
        name = CatalogNames.clean(name),
        barcode = barcode.trim(),
        values = values.mapValues { CatalogNames.clean(it.value) }.filterValues { it.isNotEmpty() },
        fitsInto = fitsInto.clean(),
        parts = parts.clean(),
    )

    fun withPart(part: LinkDraft): ItemDraft =
        if (part.title.isBlank() || parts.any { it.key == part.key }) this else copy(parts = parts + part.copy(title = CatalogNames.clean(part.title)))

    fun withFitsInto(link: LinkDraft): ItemDraft =
        if (link.title.isBlank() || fitsInto.any { it.key == link.key }) this else copy(fitsInto = fitsInto + link.copy(title = CatalogNames.clean(link.title)))

    private fun List<LinkDraft>.clean() =
        map { it.copy(title = CatalogNames.clean(it.title)) }.filter { it.title.isNotEmpty() }.distinctBy { it.key }

    companion object {
        fun from(item: CatalogItem) = ItemDraft(
            id = item.id,
            typeId = item.typeId,
            name = item.name,
            values = item.values,
            fitsInto = item.fitsInto.map(LinkDraft::from),
            parts = item.parts.map(LinkDraft::from),
            barcode = item.barcode,
            priceDigits = item.price.dram.takeIf { it > 0 }?.toString().orEmpty(),
            costDigits = item.cost.dram.takeIf { it > 0 }?.toString().orEmpty(),
        )
    }
}

enum class ItemDraftError { TYPE_REQUIRED, NAME_REQUIRED }

/**
 * A part in the client-item form: a catalog item ([itemId], or a new one by [title]) or, with
 * neither, what it should be, described by [values]. [id] is the client's row, 0 = new.
 */
data class ClientPartDraft(
    val id: Long = 0,
    val typeId: Long,
    val itemId: Long = 0,
    val title: String = "",
    val values: Map<Long, String> = emptyMap(),
    val serialNumber: String = "",
    val note: String = "",
) {
    /** Described by its values, not a catalog item. */
    val isSpec: Boolean get() = itemId == 0L && title.isBlank()

    fun link() = LinkDraft(itemId, typeId, title)

    fun withValue(fieldId: Long, value: String) =
        copy(values = if (value.isBlank()) values - fieldId else values + (fieldId to value))

    /** "CF283A", or "Size 30 · Boy" for a described one. */
    fun title(type: ItemType?): String = title.ifBlank { type?.let { ItemTitles.describe(it, values) }.orEmpty() }.ifEmpty { type?.name.orEmpty() }

    companion object {
        fun from(part: ClientItemPart) = ClientPartDraft(
            id = part.id,
            typeId = part.type.id,
            itemId = part.item?.id ?: 0,
            title = part.item?.title.orEmpty(),
            values = if (part.item == null) part.values else emptyMap(),
            serialNumber = part.serialNumber,
            note = part.note,
        )
    }
}

/**
 * The client-item form. [item] is the catalog model picked or typed as a new one; for a type whose
 * things are each different (a child) it's the item's own name and values and never goes to the catalog.
 */
data class ClientItemDraft(
    val id: Long = 0,
    val clientId: Long = 0,
    val item: ItemDraft = ItemDraft(),
    val parts: List<ClientPartDraft> = emptyList(),
    val place: String = "",
    val note: String = "",
    /** As on the label, typed or scanned. */
    val serialNumber: String = "",
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): Set<ItemDraftError> = item.validate()

    /** A catalog part already listed is not added twice; described parts can repeat (two pairs of shoes). */
    fun addPart(part: ClientPartDraft): ClientItemDraft {
        if (part.isSpec && part.values.isEmpty() && part.serialNumber.isBlank()) return this
        if (!part.isSpec && parts.any { !it.isSpec && it.link().key == part.link().key }) return this
        return copy(parts = parts + part.copy(title = CatalogNames.clean(part.title)))
    }

    fun withPart(index: Int, part: ClientPartDraft) = copy(parts = parts.mapIndexed { i, p -> if (i == index) part else p })

    fun removePart(index: Int) = copy(parts = parts.filterIndexed { i, _ -> i != index })

    /** Picking a catalog model lists all its parts, the usual case; described parts stay. */
    fun withItem(model: CatalogItem): ClientItemDraft = copy(
        item = ItemDraft.from(model),
        parts = parts.filter { it.isSpec } + model.parts.map { ClientPartDraft(typeId = it.typeId, itemId = it.id, title = it.title) },
    )

    companion object {
        fun from(owned: ClientItem) = ClientItemDraft(
            id = owned.id,
            clientId = owned.clientId,
            item = owned.item?.let(ItemDraft::from) ?: ItemDraft(typeId = owned.type.id, name = owned.name, values = owned.values),
            parts = owned.parts.map(ClientPartDraft::from),
            place = owned.place,
            note = owned.note,
            serialNumber = owned.serialNumber,
        )
    }
}
