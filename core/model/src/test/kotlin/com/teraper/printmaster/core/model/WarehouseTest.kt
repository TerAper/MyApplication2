package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseTest {

    private val cartridge = CatalogItem(1, ItemType(1, "Cartridge"), "CF283A", cost = Money.ofDram(7_000))

    @Test
    fun lowWhenUnderWhatToKeep() {
        assertTrue(StockLine(cartridge, 1, keepAtLeast = 4).isLow)
        assertEquals(3, StockLine(cartridge, 1, keepAtLeast = 4).toBuy)
        assertFalse(StockLine(cartridge, 4, keepAtLeast = 4).isLow)
        // Nothing to keep: never low, even at zero.
        assertFalse(StockLine(cartridge, 0).isLow)
        // A negative count (used more than recorded) buys back to the target, not past it.
        assertEquals(4, StockLine(cartridge, -2, keepAtLeast = 4).toBuy)
    }

    @Test
    fun valueIsCostTimesPieces() {
        assertEquals(Money.ofDram(21_000), StockLine(cartridge, 3).value)
        assertEquals(Money.ZERO, StockLine(cartridge, -1).value)
    }

    @Test
    fun filters() {
        val low = StockLine(cartridge, 1, 4)
        val out = StockLine(cartridge, 0)
        assertTrue(StockFilter.IN_STOCK.accepts(low))
        assertFalse(StockFilter.IN_STOCK.accepts(out))
        assertTrue(StockFilter.LOW.accepts(low))
        assertTrue(StockFilter.OUT.accepts(out))
        assertTrue(StockFilter.ALL.accepts(out))
    }

    @Test
    fun quantityKeepsDigitsAndAZero() {
        val draft = StockDraft(1)
        assertEquals("12", draft.withQuantity("0012").quantityDigits)
        assertEquals("0", draft.withQuantity("000").quantityDigits)
        assertEquals("", draft.withQuantity("ab").quantityDigits)
        assertEquals("123456", draft.withQuantity("12345678").quantityDigits)
    }

    @Test
    fun changeByKind() {
        assertEquals(5, StockDraft(1, StockKind.RECEIVE, "5").change(3))
        assertEquals(-2, StockDraft(1, StockKind.ADJUST, "1").change(3))
        assertEquals(-2, StockDraft(1, StockKind.MOVE, "2").change(3))
    }

    @Test
    fun validation() {
        assertEquals(setOf(StockDraftError.QUANTITY_REQUIRED), StockDraft(1, StockKind.RECEIVE, "0").validate(3))
        assertEquals(emptySet<StockDraftError>(), StockDraft(1, StockKind.ADJUST, "0").validate(3))
        assertEquals(setOf(StockDraftError.NO_CHANGE), StockDraft(1, StockKind.ADJUST, "3").validate(3))
        assertEquals(setOf(StockDraftError.QUANTITY_REQUIRED), StockDraft(1, StockKind.ADJUST, "").validate(3))
        assertEquals(
            setOf(StockDraftError.NOT_ENOUGH, StockDraftError.COMPANY_REQUIRED),
            StockDraft(1, StockKind.MOVE, "4").validate(3),
        )
        assertEquals(emptySet<StockDraftError>(), StockDraft(1, StockKind.MOVE, "3", toCompanyId = 2).validate(3))
    }
}
