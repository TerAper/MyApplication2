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

    private val toner = CatalogItem(10, ItemType(1, "Cartridge"), "CF283A", price = Money.ofDram(9_000), cost = Money.ofDram(7_000))

    @Test
    fun goodsBeyondStockAreMarkedToBuyAndStaySo() {
        val one = RepairDraft(orderId = 1).plus(toner, inStock = 1)
        assertEquals(listOf(false), one.lines.map { it.toBuy })
        val two = one.plus(toner, inStock = 1)
        assertEquals(listOf(true), two.lines.map { it.toBuy })
        // Unmarked by hand it stays so; services can't be marked.
        val unmarked = two.withToBuy(0, false).plus(refill).withToBuy(1, true)
        assertEquals(listOf(false, false), unmarked.lines.map { it.toBuy })
    }

    @Test
    fun shortagesCoverToBuyAndMissingStock() {
        val work = OrderWork(
            listOf(
                Repair(1, 1, lines = listOf(RepairLine(null, "CF283A", Money.ofDram(9_000), quantity = 2, itemId = 10, toBuy = true))),
                Repair(2, 1, lines = listOf(RepairLine(null, "CF283A", Money.ofDram(9_000), itemId = 10), RepairLine(null, "Drum", Money.ofDram(5_000), quantity = 3, itemId = 11))),
            ),
        )
        assertEquals(mapOf(10L to 3, 11L to 3), work.goodsNeeded)
        // 3 toners, 2 of them to buy, 2 in stock: buy 2. 3 drums, 1 in stock: buy 2.
        assertEquals(listOf(10L to 2, 11L to 2), work.goodsShortages(mapOf(10L to 2, 11L to 1)).map { it.itemId to it.missing })
        // To-buy toners must be bought even with enough in stock; enough drums: no shortage.
        assertEquals(listOf(10L to 2), work.goodsShortages(mapOf(10L to 9, 11L to 3)).map { it.itemId to it.missing })
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
