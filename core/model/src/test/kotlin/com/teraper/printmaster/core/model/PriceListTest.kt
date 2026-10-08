package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceListTest {

    @Test
    fun draftNeedsNameAndPrice() {
        assertEquals(setOf(PriceItemError.NAME_REQUIRED, PriceItemError.PRICE_REQUIRED), PriceItemDraft().validate())
        assertTrue(PriceItemDraft(name = "Refill", priceDigits = "3000").validate().isEmpty())
    }

    @Test
    fun amountsKeepOnlyDigits() {
        val draft = PriceItemDraft().withPrice("03 500 ֏").withCost("1.200")
        assertEquals("3500", draft.priceDigits)
        assertEquals("1200", draft.costDigits)
        assertEquals(Money.ofDram(2_300), draft.profit)
    }

    @Test
    fun draftFromItemLeavesZeroCostEmpty() {
        val draft = PriceItemDraft.from(PriceItem(4, RepairCategory.PRINTER, "Roller", price = Money.ofDram(5_000)))
        assertEquals("5000", draft.priceDigits)
        assertEquals("", draft.costDigits)
    }

    @Test
    fun searchLooksInNameAndDescription() {
        val item = PriceItem(1, RepairCategory.CARTRIDGE, "Լիցքավորում 85A", "HP, Canon 725", Money.ofDram(3_000))
        assertTrue(PriceListSearch.matches(item, "լից canon"))
        assertTrue(PriceListSearch.matches(item, ""))
        assertFalse(PriceListSearch.matches(item, "drum"))
        assertEquals(PriceListSearch.key("Լիցքավորում  85A"), PriceListSearch.key("լիցքավորում 85a"))
    }
}
