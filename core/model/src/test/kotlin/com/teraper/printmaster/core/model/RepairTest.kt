package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairTest {

    private val refill = PriceItem(1, RepairCategory.CARTRIDGE, "Refill", price = Money.ofDram(3_000), cost = Money.ofDram(900))
    private val chip = PriceItem(2, RepairCategory.CARTRIDGE, "Chip", price = Money.ofDram(2_500), cost = Money.ofDram(1_200))

    @Test
    fun sameItemTwiceCountsTwice() {
        val draft = RepairDraft(orderId = 1).plus(refill).plus(chip).plus(refill)
        assertEquals(listOf(2, 1), draft.lines.map { it.quantity })
        assertEquals(Money.ofDram(8_500), draft.total)
        assertEquals(Money.ofDram(3_000), draft.cost)
        assertEquals(Money.ofDram(5_500), draft.profit)
        assertEquals(mapOf(1L to 2, 2L to 1), draft.quantities())
    }

    @Test
    fun quantityZeroRemovesLineAndPriceCanChange() {
        val draft = RepairDraft(orderId = 1).plus(refill).plus(chip)
            .withQuantity(0, 0)
            .withPrice(0, Money.ofDram(2_000))
        assertEquals(listOf("Chip"), draft.lines.map { it.name })
        assertEquals(Money.ofDram(2_000), draft.total)
        assertEquals(Money.ofDram(1_200), draft.cost)
    }

    @Test
    fun needsAtLeastOneLine() {
        assertEquals(setOf(RepairDraftError.LINES_REQUIRED), RepairDraft(orderId = 1).validate())
        assertTrue(RepairDraft(orderId = 1).plus(chip).validate().isEmpty())
    }

    @Test
    fun deviceAndSuggestedCategory() {
        val none = RepairDraft(orderId = 1).withDevice(null, cartridgeId = 5)
        assertNull(none.cartridgeId)
        assertNull(none.suggestedCategory())
        assertEquals(RepairCategory.PRINTER, none.withDevice(3).suggestedCategory())
        assertEquals(RepairCategory.CARTRIDGE, none.withDevice(3, 5).suggestedCategory())
    }

    @Test
    fun deviceName() {
        val model = PrinterModel(1, 1, "HP", "M125", PrintType.LASER, ColorType.MONO)
        val printer = ClientPrinter(7, 1, model)
        assertEquals("HP M125", RepairDevice(printer).name)
        assertEquals("CF283A · HP M125", RepairDevice(printer, ClientPrinterCartridge(9, Cartridge(2, "CF283A"))).name)
    }
}
