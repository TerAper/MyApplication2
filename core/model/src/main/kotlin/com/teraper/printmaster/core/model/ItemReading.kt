package com.teraper.printmaster.core.model

/**
 * What an AI read from a photo of a product's box or label, in plain names as it answered. Nothing
 * here is trusted: [ItemReadings.apply] keeps only what fits the user's own types and fields.
 */
data class ItemReading(
    val type: String = "",
    val name: String = "",
    /** Field name → value. */
    val fields: Map<String, String> = emptyMap(),
    /** Models this item fits into (a cartridge's printers). */
    val fitsInto: List<String> = emptyList(),
)

object ItemReadings {

    /**
     * The question for the AI: the user's types with their fields, so the answer uses them.
     * [barcode] is a hint only; the photo is what counts. [language]: "hy" or "en", for free text.
     */
    fun prompt(types: List<ItemType>, barcode: String, language: String): String = buildString {
        appendLine("You read a photo of a product, its box or its label, for a small business's catalog.")
        appendLine("Use only what is printed or clearly visible. Never guess a value you cannot read; leave it out.")
        if (barcode.isNotBlank()) appendLine("Its barcode is $barcode (a hint only; trust the photo).")
        appendLine("Pick the one type it is from this list (use the exact type name), or \"\" if none fits:")
        for (type in types.filterNot { it.perClient }) {
            val parent = types.firstOrNull { it.id == type.parentId }?.name
            append("- \"").append(type.name).append('"')
            if (parent != null) append(" (fits into \"").append(parent).append("\")")
            if (type.fields.isNotEmpty()) {
                append("; fields: ")
                append(
                    type.fields.filter { it.kind != FieldKind.DATE }.joinToString("; ") { f ->
                        when (f.kind) {
                            FieldKind.CHOICE -> "\"${f.name}\" one of ${f.options.joinToString(", ") { "\"$it\"" }}"
                            FieldKind.NUMBER -> "\"${f.name}\" a number"
                            FieldKind.YES_NO -> "\"${f.name}\" yes or no"
                            else -> "\"${f.name}\" text"
                        }
                    },
                )
            }
            appendLine()
        }
        appendLine("\"name\" is the model or product name without the brand when the type has a brand field (for example \"LaserJet M125\", \"CF283A\").")
        appendLine("\"fitsInto\": for a part or consumable, the model names it is printed to fit (for example compatible printers); else [].")
        appendLine("Write free text in ${if (language == "en") "English" else "the language printed on the label"}.")
        append("Answer only JSON: {\"type\": \"\", \"name\": \"\", \"fields\": {\"field name\": \"value\"}, \"fitsInto\": []}")
    }

    /**
     * [draft] with what the reading adds: the type only when none was chosen, and then only into
     * empty places (name, fields, models it fits into). [known] are catalog items to link to by title.
     */
    fun apply(draft: ItemDraft, reading: ItemReading, types: List<ItemType>, known: List<ItemRef> = emptyList()): ItemDraft {
        val chosen = types.firstOrNull { it.id == draft.typeId }
            ?: types.filterNot { it.perClient }.firstOrNull { it.name.equals(reading.type.trim(), ignoreCase = true) }
            ?: return if (draft.name.isBlank() && reading.name.isNotBlank()) draft.copy(name = reading.name.trim()) else draft
        var result = if (draft.typeId == 0L) draft.copy(typeId = chosen.id) else draft
        if (result.name.isBlank() && reading.name.isNotBlank()) result = result.copy(name = reading.name.trim())
        val answers = reading.fields.mapKeys { it.key.trim().lowercase() }
        for (field in chosen.fields) {
            if (result.values[field.id].orEmpty().isNotBlank()) continue
            val answer = answers[field.name.trim().lowercase()]?.trim().orEmpty()
            val value = when (field.kind) {
                FieldKind.CHOICE -> field.options.firstOrNull { it.equals(answer, ignoreCase = true) }
                FieldKind.NUMBER -> answer.replace(',', '.').takeIf { it.toDoubleOrNull() != null }
                FieldKind.YES_NO -> ItemTitles.YES.takeIf { answer.lowercase() in YES_WORDS }
                FieldKind.DATE -> null
                FieldKind.TEXT -> answer.takeIf { it.isNotEmpty() }
            }
            if (value != null) result = result.withValue(field.id, value)
        }
        val parent = types.firstOrNull { it.id == chosen.parentId }
        if (parent != null) {
            for (title in reading.fitsInto.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_FITS)) {
                val match = known.firstOrNull { it.typeId == parent.id && CatalogNames.codeKey(it.title).endsWith(CatalogNames.codeKey(title)) }
                result = result.withFitsInto(match?.let(LinkDraft::from) ?: LinkDraft(typeId = parent.id, title = title))
            }
        }
        return result
    }

    private val YES_WORDS = setOf("yes", "true", "1", "այո")
    private const val MAX_FITS = 20
}
