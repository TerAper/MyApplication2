package com.teraper.printmaster.core.model

import java.util.Locale

data class Chip(val id: Long, val name: String)

/** A cartridge type, e.g. "CF283A". The same cartridge can fit several printer models. */
data class Cartridge(val id: Long, val name: String, val chips: List<Chip> = emptyList())

/** A printer model from the catalog, e.g. HP "LaserJet M125". */
data class PrinterModel(
    val id: Long,
    val brandId: Long,
    val brand: String,
    val name: String,
    val printType: PrintType,
    val colorType: ColorType,
    val cartridges: List<Cartridge> = emptyList(),
) {
    val fullName: String get() = "$brand $name"
}

/** A catalog model plus how many client printers are of that model. */
data class CatalogModel(val model: PrinterModel, val printerCount: Int = 0)

/** A client who owns a printer of some model ("who has this printer?"). */
data class ModelOwner(val clientId: Long, val clientName: String, val location: String)

/** One printer a client owns. */
data class ClientPrinter(
    val id: Long,
    val clientId: Long,
    val model: PrinterModel,
    val location: String = "",
    val note: String = "",
    val cartridges: List<ClientPrinterCartridge> = emptyList(),
)

/** A cartridge the client's printer uses; [id] is kept so repairs stay linked. */
data class ClientPrinterCartridge(val id: Long, val cartridge: Cartridge)

/** Name rules shared by every catalog entry, so "hp", "HP " and "Hp" are one brand. */
object CatalogNames {
    private val SPACES = Regex("""\s+""")
    private val CODE_NOISE = Regex("""[\s\-_./]""")

    /** Trimmed, single spaces: what gets stored. */
    fun clean(name: String): String = name.trim().replace(SPACES, " ")

    /** Brand / model key: case and spacing don't matter. */
    fun key(name: String): String = clean(name).lowercase(Locale.ROOT)

    /** Cartridge / chip key: "CF 283A", "cf-283a" and "CF283A" are the same code. */
    fun codeKey(name: String): String = name.replace(CODE_NOISE, "").uppercase(Locale.ROOT)

    /** Several chips can be typed in one field: "chip A, chip B". */
    fun splitList(text: String): List<String> =
        text.split(',', ';').map(::clean).filter { it.isNotEmpty() }.distinctBy(::codeKey)
}

/** Catalog search: every word must be found in brand, model, cartridge or chip names. */
object CatalogSearch {
    fun matches(model: PrinterModel, query: String): Boolean {
        val words = ClientSearch.normalizeText(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = buildString {
            append(ClientSearch.normalizeText(model.fullName)).append(' ')
            model.cartridges.forEach { cartridge ->
                append(ClientSearch.normalizeText(cartridge.name)).append(' ')
                cartridge.chips.forEach { append(ClientSearch.normalizeText(it.name)).append(' ') }
            }
        }
        // Codes without spaces too, so "cf283" finds "CF 283A".
        val codes = buildString {
            append(CatalogNames.codeKey(model.name)).append(' ')
            model.cartridges.forEach { cartridge ->
                append(CatalogNames.codeKey(cartridge.name)).append(' ')
                cartridge.chips.forEach { append(CatalogNames.codeKey(it.name)).append(' ') }
            }
        }
        return words.all { it in text || CatalogNames.codeKey(it) in codes }
    }
}
