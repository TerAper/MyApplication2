package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveMoneyEntryResult
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import com.teraper.printmaster.core.model.MoneyEntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class OfflinePaymentsRepositoryTest {

    private lateinit var db: PrintMasterDatabase
    private lateinit var clients: OfflineClientsRepository
    private lateinit var payments: OfflinePaymentsRepository
    private var clientId = 0L
    private val day = LocalDate.of(2026, 10, 7)

    @Before
    fun setUp() = runTest {
        db = PrintMasterDatabase.create(ApplicationProvider.getApplicationContext(), inMemory = true)
        val clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)
        clients = OfflineClientsRepository(db, db.clientDao(), clock)
        payments = OfflinePaymentsRepository(db.ledgerDao(), db.clientDao(), clock)
        clientId = (clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(kind: MoneyEntryKind, dram: Long, date: LocalDate = day) =
        payments.saveMoneyEntry(MoneyEntryDraft(kind, clientId, dram.toString(), date, " note "))

    @Test
    fun cashAndDebtUpdateBalanceAndLedger() = runTest {
        save(MoneyEntryKind.MANUAL_CHARGE, 50_000, day.minusDays(3))
        save(MoneyEntryKind.CASH_PAYMENT, 17_000)

        val summary = clients.observeClientSummary(clientId).first()!!
        assertEquals(Money.ofDram(50_000), summary.charged)
        assertEquals(Money.ofDram(17_000), summary.paid)
        assertEquals(Money.ofDram(33_000), summary.balance)
        assertEquals(day, summary.lastPaymentDate)

        val ledger = payments.observeLedger(clientId).first()
        assertTrue(ledger[0] is LedgerEntry.Payment)
        assertTrue(ledger[1] is LedgerEntry.Charge)
        assertEquals("note", ledger[0].note)
    }

    @Test
    fun incomeCountsOnlyTheGivenPeriod() = runTest {
        save(MoneyEntryKind.CASH_PAYMENT, 10_000, day)
        save(MoneyEntryKind.CASH_PAYMENT, 5_000, day.minusMonths(1))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO payments (client_id, method, amount_minor, date_epoch_day, reference, note, created_at) " +
                "VALUES ($clientId, 'BANK', 2000000, ${day.toEpochDay()}, '88231', '', 0)",
        )

        val income = payments.observeIncome(day.withDayOfMonth(1), day).first()
        assertEquals(IncomeTotals(cash = Money.ofDram(10_000), bank = Money.ofDram(20_000)), income)
    }

    @Test
    fun onlyCashAndManualEntriesCanBeDeleted() = runTest {
        save(MoneyEntryKind.CASH_PAYMENT, 1_000)
        save(MoneyEntryKind.MANUAL_CHARGE, 2_000)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO payments (client_id, method, amount_minor, date_epoch_day, reference, note, created_at) " +
                "VALUES ($clientId, 'BANK', 300000, ${day.toEpochDay()}, '1', '', 0)",
        )

        val ledger = payments.observeLedger(clientId).first()
        val bank = ledger.first { it is LedgerEntry.Payment && !it.canDelete }
        assertFalse(payments.deleteEntry(bank))
        ledger.filter { it.canDelete }.forEach { assertTrue(payments.deleteEntry(it)) }

        assertEquals(listOf(bank.id), payments.observeLedger(clientId).first().map { it.id })
    }

    @Test
    fun invalidOrUnknownClientIsRejected() = runTest {
        assertEquals(
            SaveMoneyEntryResult.Invalid(setOf(MoneyEntryError.AMOUNT_REQUIRED)),
            payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, clientId, "", day)),
        )
        assertEquals(
            SaveMoneyEntryResult.Invalid(setOf(MoneyEntryError.CLIENT_REQUIRED)),
            payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, 999, "5", day)),
        )
    }
}
