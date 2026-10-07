package com.teraper.printmaster.core.model

/** A cartridge row in a form; [chips] is typed as one comma-separated field. */
data class CartridgeDraft(val name: String = "", val chips: String = "") {
    val key: String get() = CatalogNames.codeKey(name)
    fun chipNames(): List<String> = CatalogNames.splitList(chips)

    companion object {
        fun from(cartridge: Cartridge) = CartridgeDraft(cartridge.name, cartridge.chips.joinToString(", ") { it.name })
    }
}

/** A printer model as typed in a form. [id] 0 = a model not in the catalog yet. */
data class PrinterModelDraft(
    val id: Long = 0,
    val brand: String = "",
    val name: String = "",
    val printType: PrintType = PrintType.LASER,
    val colorType: ColorType = ColorType.MONO,
    val cartridges: List<CartridgeDraft> = emptyList(),
) {
    val isNew: Boolean get() = id == 0L
    val fullName: String get() = CatalogNames.clean("$brand $name")

    fun validate(): Set<PrinterDraftError> = buildSet {
        if (brand.isBlank()) add(PrinterDraftError.BRAND_REQUIRED)
        if (name.isBlank()) add(PrinterDraftError.MODEL_REQUIRED)
    }

    /** Trimmed names, empty and repeated cartridges dropped. */
    fun normalized(): PrinterModelDraft = copy(
        brand = CatalogNames.clean(brand),
        name = CatalogNames.clean(name),
        cartridges = cartridges
            .map { CartridgeDraft(CatalogNames.clean(it.name), it.chipNames().joinToString(", ")) }
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.key },
    )

    /** Adds a cartridge unless one with the same code is already listed. */
    fun withCartridge(cartridge: CartridgeDraft): PrinterModelDraft =
        if (cartridge.name.isBlank() || cartridges.any { it.key == cartridge.key }) this
        else copy(cartridges = cartridges + cartridge.copy(name = CatalogNames.clean(cartridge.name)))

    companion object {
        fun from(model: PrinterModel) = PrinterModelDraft(
            id = model.id,
            brand = model.brand,
            name = model.name,
            printType = model.printType,
            colorType = model.colorType,
            cartridges = model.cartridges.map(CartridgeDraft::from),
        )
    }
}

/**
 * The client-printer form. The model is either picked from the catalog or typed
 * as a new one; [selectedCartridges] holds cartridge keys ([CartridgeDraft.key])
 * of the model's cartridges this printer uses.
 */
data class ClientPrinterDraft(
    val id: Long = 0,
    val clientId: Long = 0,
    val model: PrinterModelDraft = PrinterModelDraft(),
    val selectedCartridges: Set<String> = emptySet(),
    val location: String = "",
    val note: String = "",
) {
    val isNew: Boolean get() = id == 0L

    fun validate(): Set<PrinterDraftError> = model.validate()

    /** The cartridges of the model that are ticked for this printer. */
    fun chosenCartridges(): List<CartridgeDraft> = model.cartridges.filter { it.key in selectedCartridges }

    fun toggleCartridge(key: String): ClientPrinterDraft =
        copy(selectedCartridges = if (key in selectedCartridges) selectedCartridges - key else selectedCartridges + key)

    /** A new cartridge typed in the form: added to the model and ticked. */
    fun addCartridge(cartridge: CartridgeDraft): ClientPrinterDraft {
        if (cartridge.name.isBlank()) return this
        return copy(model = model.withCartridge(cartridge), selectedCartridges = selectedCartridges + cartridge.key)
    }

    /** Picking a catalog model ticks all its cartridges, the usual case. */
    fun withModel(model: PrinterModel): ClientPrinterDraft {
        val draft = PrinterModelDraft.from(model)
        return copy(model = draft, selectedCartridges = draft.cartridges.mapTo(mutableSetOf()) { it.key })
    }

    companion object {
        fun from(printer: ClientPrinter): ClientPrinterDraft {
            // Keep cartridges the printer has even if they were later removed from the model.
            val model = printer.cartridges.fold(PrinterModelDraft.from(printer.model)) { draft, owned ->
                draft.withCartridge(CartridgeDraft.from(owned.cartridge))
            }
            return ClientPrinterDraft(
                id = printer.id,
                clientId = printer.clientId,
                model = model,
                selectedCartridges = printer.cartridges.mapTo(mutableSetOf()) { CatalogNames.codeKey(it.cartridge.name) },
                location = printer.location,
                note = printer.note,
            )
        }
    }
}

enum class PrinterDraftError { BRAND_REQUIRED, MODEL_REQUIRED }
