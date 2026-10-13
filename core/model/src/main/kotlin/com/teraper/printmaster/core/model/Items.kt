package com.teraper.printmaster.core.model

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The customer's own kinds of things. A type ("Printer", "Shirt") has fields the customer
 * chose; a type can be part of another one ("Cartridge" goes into "Printer"). Catalog items are
 * the real models ("HP LaserJet M125"), and client items are the ones a client owns.
 */

/** What a field holds; choices are typed by the customer. A date is kept as yyyy-MM-dd and shown as an age. */
enum class FieldKind { TEXT, NUMBER, CHOICE, YES_NO, DATE }

/**
 * One field of a type. [inTitle] fields are written before the item's name, so a "Brand" field
 * makes "HP" + "LaserJet M125" read "HP LaserJet M125".
 */
data class ItemField(
    val id: Long,
    val typeId: Long,
    val name: String,
    val kind: FieldKind = FieldKind.TEXT,
    val options: List<String> = emptyList(),
    val inTitle: Boolean = false,
)

/**
 * A kind of thing; [parentId] is the type it goes into (a cartridge into a printer), null = stands alone.
 * [hasSerial]: things of this type have a serial number to keep; [hasPhotos]: photos are taken of them. [perClient]: each one is different
 * (a client's child), so it's described on the client, not picked from the catalog.
 */
data class ItemType(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val fields: List<ItemField> = emptyList(),
    val hasSerial: Boolean = true,
    val perClient: Boolean = false,
    val hasPhotos: Boolean = true,
) {
    val titleFields: List<ItemField> get() = fields.filter { it.inTitle }
}

/** A type with how deep it sits under its parents, for showing the types as a tree. */
data class TypeInTree(val type: ItemType, val depth: Int)

object ItemTypes {
    /** Parents first, each followed by its own parts' types; siblings by name. */
    fun tree(types: List<ItemType>): List<TypeInTree> {
        val byParent = types.groupBy { type -> type.parentId?.takeIf { id -> types.any { it.id == id } } }
        val out = ArrayList<TypeInTree>(types.size)
        fun add(parentId: Long?, depth: Int) {
            byParent[parentId].orEmpty().sortedBy { CatalogNames.key(it.name) }.forEach {
                out += TypeInTree(it, depth)
                add(it.id, depth + 1)
            }
        }
        add(null, 0)
        return out
    }

    /** The types whose items go into items of [typeId]. */
    fun partTypes(types: List<ItemType>, typeId: Long): List<ItemType> =
        types.filter { it.parentId == typeId }.sortedBy { CatalogNames.key(it.name) }

    /** [typeId] and every type under it: what it can't be put into without a loop. */
    fun selfAndBelow(types: List<ItemType>, typeId: Long): Set<Long> {
        val out = mutableSetOf(typeId)
        var added = true
        while (added) {
            added = types.filter { it.parentId in out && it.id !in out }.map { out.add(it.id) }.isNotEmpty()
        }
        return out
    }
}

/** A catalog item named by its title, e.g. a printer a cartridge fits. */
data class ItemRef(val id: Long, val typeId: Long, val typeName: String, val title: String)

/**
 * A real model in the catalog. [values] by field id. [fitsInto] are items of the parent type it
 * goes into; [parts] are items of child types that go into it. [price] is what a client pays for
 * it and [cost] what it costs you, for selling it as goods; zero = not sold.
 */
data class CatalogItem(
    val id: Long,
    val type: ItemType,
    val name: String,
    val values: Map<Long, String> = emptyMap(),
    val fitsInto: List<ItemRef> = emptyList(),
    val parts: List<ItemRef> = emptyList(),
    /** As printed under the bars; empty = none. */
    val barcode: String = "",
    val price: Money = Money.ZERO,
    val cost: Money = Money.ZERO,
) {
    /** Has a price: offered as goods when work is written down. */
    val isGoods: Boolean get() = price.isPositive

    val typeId: Long get() = type.id

    /** Title fields, then the name: "HP LaserJet M125". */
    val title: String get() = ItemTitles.title(type, name, values)

    /** The other filled fields: "Laser · Black/white", "Size 42". Yes/no fields show their name when yes. */
    val details: String get() = ItemTitles.details(type, values)

    fun ref() = ItemRef(id, type.id, type.name, title)
}

object ItemTitles {
    fun title(type: ItemType, name: String, values: Map<Long, String>): String =
        CatalogNames.clean((type.titleFields.mapNotNull { values[it.id]?.takeIf(String::isNotBlank) } + name).joinToString(" "))

    /** [years] words an age ("6 y"); a date field shows the age it gives today. */
    fun details(
        type: ItemType,
        values: Map<Long, String>,
        today: LocalDate = LocalDate.now(),
        years: (Int) -> String = { it.toString() },
    ): String = type.fields.filterNot { it.inTitle }.mapNotNull { field ->
        val value = values[field.id]?.trim().orEmpty()
        when {
            value.isEmpty() -> null
            field.kind == FieldKind.YES_NO -> field.name.takeIf { value == YES }
            field.kind == FieldKind.NUMBER -> "${field.name} $value"
            field.kind == FieldKind.DATE -> age(value, today)?.let(years) ?: value
            else -> value
        }
    }.joinToString(" · ")

    /** Title fields and the other filled fields: "Size 30 · Boy", what a part asked for by its values reads like. */
    fun describe(type: ItemType, values: Map<Long, String>, today: LocalDate = LocalDate.now(), years: (Int) -> String = { it.toString() }): String =
        listOf(title(type, "", values), details(type, values, today, years)).filter { it.isNotBlank() }.joinToString(" · ")

    /** Full years from a stored date to [today]; null = not a date. */
    fun age(date: String, today: LocalDate = LocalDate.now()): Int? =
        parseDate(date)?.let { Period.between(it, today).years.coerceAtLeast(0) }

    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** How a yes/no field stores "yes"; empty = no. */
    const val YES = "1"
}

/** A catalog item plus how many client items are of it. */
data class CatalogEntry(val item: CatalogItem, val ownedCount: Int = 0)

/** A client who owns an item of some catalog model ("who has this?"). */
data class ItemOwner(val clientId: Long, val clientName: String, val place: String)

/**
 * One thing a client owns or has: a catalog model ([item], a printer) or one of a kind (a child),
 * then described by its own [name] and [values].
 */
data class ClientItem(
    val id: Long,
    val clientId: Long,
    val type: ItemType,
    val item: CatalogItem? = null,
    val name: String = "",
    val values: Map<Long, String> = emptyMap(),
    /** Where it stands, e.g. "Accounting room". */
    val place: String = "",
    val note: String = "",
    val parts: List<ClientItemPart> = emptyList(),
    /** As on the label; empty = not known. */
    val serialNumber: String = "",
) {
    constructor(id: Long, clientId: Long, item: CatalogItem, place: String = "", note: String = "", parts: List<ClientItemPart> = emptyList(), serialNumber: String = "") :
        this(id, clientId, item.type, item, place = place, note = note, parts = parts, serialNumber = serialNumber)

    /** "HP LaserJet M125", or a child's name. */
    val title: String get() = item?.title ?: ItemTitles.title(type, name, values)

    /** The field values: the model's, or its own. */
    val allValues: Map<Long, String> get() = item?.values ?: values

    /** "HP LaserJet M125 (Office)", so two of one model read differently. */
    val label: String get() = title + if (place.isBlank()) "" else " ($place)"
}

/**
 * A part of a client's item: a catalog item ([item], the cartridge of their printer) or what it should
 * be, described by [values] (a child's shoes: size 30). [id] is kept so work stays linked.
 */
data class ClientItemPart(
    val id: Long,
    val type: ItemType,
    val item: CatalogItem? = null,
    val values: Map<Long, String> = emptyMap(),
    val serialNumber: String = "",
    val note: String = "",
) {
    constructor(id: Long, item: CatalogItem) : this(id, item.type, item)

    /** "CF283A", or "Size 30 · Boy" for one described by its values, or the type's name when nothing is filled. */
    val title: String get() = item?.title ?: ItemTitles.describe(type, values).ifEmpty { type.name }
}

/** What fits what a client's part asks for: the same catalog item, or one of its type whose values agree. */
object StockMatch {
    fun fits(part: ClientItemPart, candidate: CatalogItem): Boolean {
        if (candidate.typeId != part.type.id) return false
        part.item?.let { return it.id == candidate.id }
        return part.type.fields.all { field -> agree(field, part.values[field.id], candidate.values[field.id]) }
    }

    /** A value missing on either side means "any": shoes with no "For" fit boys and girls. */
    private fun agree(field: ItemField, wanted: String?, has: String?): Boolean {
        val a = wanted?.trim().orEmpty()
        val b = has?.trim().orEmpty()
        if (a.isEmpty() || b.isEmpty()) return true
        return when (field.kind) {
            FieldKind.NUMBER -> a.replace(',', '.').toDoubleOrNull()?.let { it == b.replace(',', '.').toDoubleOrNull() } ?: (a == b)
            else -> CatalogNames.key(a) == CatalogNames.key(b)
        }
    }
}

/** A client item found by its serial number, with its client. [exact] false = its serial only contains what was searched. */
data class SerialMatch(val item: ClientItem, val clientName: String, val exact: Boolean)

/** Serial numbers as printed, scanned or typed compare equal. */
object SerialNumbers {
    /** "S/N:", "SN ", "Serial No." before the number on many labels; a separator must follow, so "SN12345" stays whole. */
    private val LABEL = Regex("""^\s*(?:s\s*/\s*n|sn|serial(?:\s*(?:no|number))?)\s*[.:#\-\s]\s*(?=\S{4,})""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /** What is stored and shown: trimmed, the label word and extra spaces removed. */
    fun clean(text: String): String = text.replace(LABEL, "").trim().replace(SPACES, " ")

    /** For searching: letters and digits only, upper case. "cnb8 k1-2345" → "CNB8K12345". */
    fun key(text: String): String = clean(text).uppercase().filter { it.isLetterOrDigit() }
}

/** Name rules shared by every catalog entry, so "hp", "HP " and "Hp" are one. */
object CatalogNames {
    private val SPACES = Regex("""\s+""")
    private val CODE_NOISE = Regex("""[\s\-_./]""")

    /** Trimmed, single spaces: what gets stored. */
    fun clean(name: String): String = name.trim().replace(SPACES, " ")

    /** Type and field names: case and spacing don't matter. */
    fun key(name: String): String = clean(name).lowercase(Locale.ROOT)

    /** Item names: "CF 283A", "cf-283a" and "CF283A" are the same code. */
    fun codeKey(name: String): String = name.replace(CODE_NOISE, "").uppercase(Locale.ROOT)

    /** Choices typed one per line or comma-separated. */
    fun splitList(text: String): List<String> =
        text.split(',', ';', '\n').map(::clean).filter { it.isNotEmpty() }.distinctBy(::key)
}

/** Catalog search: every word must be found in the item's title, type, fields or parts. */
object CatalogSearch {
    fun matches(item: CatalogItem, query: String): Boolean {
        val words = ClientSearch.normalizeText(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val names = listOf(item.title) + item.parts.map { it.title } + item.fitsInto.map { it.title }
        val text = (names + item.type.name + item.values.values).joinToString(" ") { ClientSearch.normalizeText(it) }
        // Codes without spaces too, so "cf283" finds "CF 283A".
        val codes = names.joinToString(" ") { CatalogNames.codeKey(it) } + " " + CatalogNames.codeKey(item.name) + " " + CatalogNames.codeKey(item.barcode)
        return words.all { it in text || CatalogNames.codeKey(it) in codes }
    }
}
