package com.teraper.printmaster.core.designsystem.component

import com.teraper.printmaster.core.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    private fun String.plain() = replace(' ', ' ')

    @Test
    fun `groups thousands with spaces and adds dram sign`() {
        assertEquals("1 240 000 ֏", Money.ofDram(1_240_000).format().plain())
    }

    @Test
    fun `small amounts have no grouping`() {
        assertEquals("500 ֏", Money.ofDram(500).format().plain())
        assertEquals("0 ֏", Money.ZERO.format().plain())
    }

    @Test
    fun `shows luma only when present`() {
        assertEquals("12 500,05 ֏", Money(1_250_005).format().plain())
        assertEquals("12 500,50 ֏", Money(1_250_050).format().plain())
    }

    @Test
    fun `negative and explicit plus sign`() {
        assertEquals("−42 500 ֏", Money.ofDram(-42_500).format().plain())
        assertEquals("+5 000", Money.ofDram(5_000).format(withSign = true, withCurrency = false).plain())
    }
}
