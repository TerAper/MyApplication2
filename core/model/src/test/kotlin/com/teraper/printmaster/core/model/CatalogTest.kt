package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    private val m125 = PrinterModel(
        id = 1, brandId = 1, brand = "HP", name = "LaserJet M125",
        printType = PrintType.LASER, colorType = ColorType.MONO,
        cartridges = listOf(Cartridge(5, "CF283A", listOf(Chip(9, "HP 83A chip")))),
    )

    @Test
    fun cartridgeCodesIgnoreCaseSpacesAndDashes() {
        assertEquals(CatalogNames.codeKey("CF283A"), CatalogNames.codeKey(" cf 283-a "))
        assertEquals("hp", CatalogNames.key("  HP "))
        assertEquals(listOf("chip A", "chip B"), CatalogNames.splitList("chip A,  chip B; chip-a, "))
    }

    @Test
    fun searchFindsModelByBrandModelCartridgeOrChip() {
        assertTrue(CatalogSearch.matches(m125, "hp m125"))
        assertTrue(CatalogSearch.matches(m125, "cf283"))
        assertTrue(CatalogSearch.matches(m125, "CF 283A"))
        assertTrue(CatalogSearch.matches(m125, "83a chip"))
        assertFalse(CatalogSearch.matches(m125, "canon"))
    }

    @Test
    fun modelDraftNeedsBrandAndName() {
        assertEquals(
            setOf(PrinterDraftError.BRAND_REQUIRED, PrinterDraftError.MODEL_REQUIRED),
            PrinterModelDraft().validate(),
        )
        assertTrue(PrinterModelDraft(brand = "HP", name = "M125").validate().isEmpty())
    }

    @Test
    fun normalizedDropsEmptyAndRepeatedCartridges() {
        val draft = PrinterModelDraft(
            brand = " HP ", name = "LaserJet  M125",
            cartridges = listOf(CartridgeDraft("CF283A"), CartridgeDraft(" "), CartridgeDraft("cf 283a", "x")),
        ).normalized()
        assertEquals("HP", draft.brand)
        assertEquals("LaserJet M125", draft.name)
        assertEquals(listOf("CF283A"), draft.cartridges.map { it.name })
    }

    @Test
    fun pickingModelTicksAllItsCartridgesAndNewOnesAreTickedToo() {
        val draft = ClientPrinterDraft(clientId = 1).withModel(m125).addCartridge(CartridgeDraft("CF283X"))

        assertEquals(listOf("CF283A", "CF283X"), draft.chosenCartridges().map { it.name })
        assertEquals(listOf("CF283X"), draft.toggleCartridge(CatalogNames.codeKey("CF283A")).chosenCartridges().map { it.name })
    }

    @Test
    fun editingPrinterKeepsCartridgesRemovedFromModel() {
        val printer = ClientPrinter(
            id = 3, clientId = 1, model = m125.copy(cartridges = emptyList()),
            cartridges = listOf(ClientPrinterCartridge(7, Cartridge(5, "CF283A"))),
        )
        val draft = ClientPrinterDraft.from(printer)
        assertEquals(listOf("CF283A"), draft.chosenCartridges().map { it.name })
    }
}
