package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `ofDram stores minor units`() {
        assertEquals(1_250_000L, Money.ofDram(12_500).minor)
    }

    @Test
    fun `arithmetic keeps exact luma`() {
        val invoice = Money(1_250_050) // 12 500.50 dram
        val paid = Money.ofDram(10_000)
        assertEquals(Money(250_050), invoice - paid)
        assertEquals(Money.ofDram(18_000), Money.ofDram(6_000) * 3)
    }

    @Test
    fun `sum of empty list is zero`() {
        assertTrue(emptyList<Money>().sum().isZero)
    }

    @Test
    fun `dram rounds toward zero`() {
        assertEquals(12_500L, Money(1_250_099).dram)
        assertEquals(-12_500L, Money(-1_250_099).dram)
    }
}
