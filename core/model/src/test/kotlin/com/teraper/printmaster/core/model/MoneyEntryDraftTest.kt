package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyEntryDraftTest {

    private val today = LocalDate.of(2026, 10, 7)
    private fun draft() = MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, date = today)

    @Test
    fun `keypad builds an amount in dram`() {
        val d = draft().typed("1").typed("7").typed("000")
        assertEquals("17000", d.amountDigits)
        assertEquals(Money.ofDram(17_000), d.amount)
        assertEquals("1700", d.typed(MoneyEntryDraft.KEY_DELETE).amountDigits)
    }

    @Test
    fun `leading zeros are ignored and length is capped`() {
        assertEquals("", draft().typed("0").typed("000").amountDigits)
        val long = (1..12).fold(draft()) { d, _ -> d.typed("9") }
        assertEquals(10, long.amountDigits.length)
    }

    @Test
    fun `client and positive amount are required`() {
        assertEquals(
            setOf(MoneyEntryError.CLIENT_REQUIRED, MoneyEntryError.COMPANY_REQUIRED, MoneyEntryError.AMOUNT_REQUIRED),
            draft().validate(),
        )
        assertTrue(draft().copy(clientId = 1, companyId = 1).typed("5").validate().isEmpty())
    }

    @Test
    fun `withAmount fills whole dram`() {
        assertEquals("180000", draft().withAmount(Money.ofDram(180_000)).amountDigits)
        assertEquals("", draft().withAmount(Money.ZERO).amountDigits)
    }

    @Test
    fun `ledger sorts newest first then by creation`() {
        val a = LedgerEntry.Payment(1, today, Money.ofDram(1), "", createdAt = 1, PaymentMethod.CASH, null)
        val b = LedgerEntry.Charge(2, today, Money.ofDram(1), "", createdAt = 5, ChargeSource.MANUAL, null)
        val c = LedgerEntry.Payment(3, today.minusDays(1), Money.ofDram(1), "", createdAt = 9, PaymentMethod.BANK, "77")
        assertEquals(listOf(2L, 1L, 3L), listOf(a, b, c).sortedNewestFirst().map { it.id })
        assertTrue(a.canDelete && b.canDelete && !c.canDelete)
    }
}
