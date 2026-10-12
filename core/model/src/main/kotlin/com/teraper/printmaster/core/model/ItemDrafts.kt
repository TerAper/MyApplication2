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
        fun from(type: ItemType) = ItemTypeDraft(type.id, type.name, type.parentId, type.fields.map(FieldDraft::from))
    }
}

enum class ItemTypeError { NAME_REQUIRED, CHOICES_REQUIRED }

/** A ready set of types (a business template), or a part of an AI suggestion. */
data class TypeTemplate(val name: String, val parentName: String? = null, val fields: List<FieldDraft> = emptyList())

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
) {
    val isNew: Boolean get() = id == 0L

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
        )
    }
}

enum class ItemDraftError { TYPE_REQUIRED, NAME_REQUIRED }

/**
 * The client-item form. The model is either picked from the catalog or typed as a new one;
 * [selectedParts] holds [LinkDraft.key]s of the model's parts this client's item uses.
 */
data class ClientItemDraft(
    val id: Long = 0,
    val clientId: Long = 0,
    val item: ItemDraft = ItemDraft(),
    val selectedParts: Set<String> = emptySet(),
    val place: String = "",
    val note: String = "",
    /** As on the label, typed or scanned. */
    val serialNumber: String = "",
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): Set<ItemDraftError> = item.validate()

    /** The parts of the model that are ticked for this item. */
    fun chosenParts(): List<LinkDraft> = item.parts.filter { it.key in selectedParts }

    fun togglePart(key: String): ClientItemDraft =
        copy(selectedParts = if (key in selectedParts) selectedParts - key else selectedParts + key)

    /** A new part typed in the form: added to the model and ticked. */
    fun addPart(part: LinkDraft): ClientItemDraft {
        if (part.title.isBlank()) return this
        return copy(item = item.withPart(part), selectedParts = selectedParts + part.key)
    }

    /** Picking a catalog model ticks all its parts, the usual case. */
    fun withItem(model: CatalogItem): ClientItemDraft {
        val draft = ItemDraft.from(model)
        return copy(item = draft, selectedParts = draft.parts.mapTo(mutableSetOf()) { it.key })
    }

    companion object {
        fun from(owned: ClientItem): ClientItemDraft {
            // Keep parts the item has even if they were later removed from the model.
            val model = owned.parts.fold(ItemDraft.from(owned.item)) { draft, part -> draft.withPart(LinkDraft.from(part.item.ref())) }
            return ClientItemDraft(
                id = owned.id,
                clientId = owned.clientId,
                item = model,
                selectedParts = owned.parts.mapTo(mutableSetOf()) { LinkDraft.keyOf(it.item.typeId, it.item.title) },
                place = owned.place,
                note = owned.note,
                serialNumber = owned.serialNumber,
            )
        }
    }
}
