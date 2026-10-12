package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairTest {

    private val refill = PriceItem(1, 2, "Refill", price = Money.ofDram(3_000), cost = Money.ofDram(900))
    private val chip = PriceItem(2, 2, "Chip", price = Money.ofDram(2_500), cost = Money.ofDram(1_200))

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
    fun aPartIsOnlyKeptWithItsItem() {
        val none = RepairDraft(orderId = 1).withDevice(null, itemPartId = 5)
        assertNull(none.itemPartId)
        assertEquals(5L, none.withDevice(3, 5).itemPartId)
    }

    @Test
    fun deviceNameAndType() {
        val printer = ItemType(1, "Printer", fields = listOf(ItemField(1, 1, "Brand", inTitle = true)))
        val cartridge = CatalogItem(2, ItemType(2, "Cartridge", 1), "CF283A")
        val owned = ClientItem(7, 1, CatalogItem(1, printer, "M125", mapOf(1L to "HP")))
        assertEquals("HP M125", RepairDevice(owned).name)
        assertEquals(1L, RepairDevice(owned).typeId)
        val onPart = RepairDevice(owned.copy(place = "Office"), ClientItemPart(9, cartridge))
        assertEquals("CF283A · HP M125 (Office)", onPart.name)
        assertEquals(2L, onPart.typeId)
    }
}
