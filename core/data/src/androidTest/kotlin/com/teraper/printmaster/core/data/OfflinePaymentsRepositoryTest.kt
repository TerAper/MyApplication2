package com.teraper.printmaster.core.data

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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OfflinePaymentsRepositoryTest {

    private lateinit var repos: TestRepos
    private lateinit var db: PrintMasterDatabase
    private lateinit var clients: OfflineClientsRepository
    private lateinit var payments: OfflinePaymentsRepository
    private var clientId = 0L
    private var companyId = 0L
    private val day = LocalDate.of(2026, 10, 7)

    @Before
    fun setUp() = runTest {
        repos = TestRepos()
        db = repos.db
        clients = repos.clients
        payments = repos.payments
        companyId = repos.register()
        clientId = (clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
    }

    @After
    fun tearDown() = db.close()

    private suspend fun save(kind: MoneyEntryKind, dram: Long, date: LocalDate = day, company: Long = companyId) =
        payments.saveMoneyEntry(MoneyEntryDraft(kind, clientId, company, dram.toString(), date, " note "))

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
            "INSERT INTO payments (company_id, client_id, method, amount_minor, date_epoch_day, reference, note, created_at) " +
                "VALUES ($companyId, $clientId, 'BANK', 2000000, ${day.toEpochDay()}, '88231', '', 0)",
        )

        val income = payments.observeIncome(day.withDayOfMonth(1), day).first()
        assertEquals(IncomeTotals(cash = Money.ofDram(10_000), bank = Money.ofDram(20_000)), income)
    }

    @Test
    fun onlyCashAndManualEntriesCanBeDeleted() = runTest {
        save(MoneyEntryKind.CASH_PAYMENT, 1_000)
        save(MoneyEntryKind.MANUAL_CHARGE, 2_000)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO payments (company_id, client_id, method, amount_minor, date_epoch_day, reference, note, created_at) " +
                "VALUES ($companyId, $clientId, 'BANK', 300000, ${day.toEpochDay()}, '1', '', 0)",
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
            payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, clientId, companyId, "", day)),
        )
        assertEquals(
            SaveMoneyEntryResult.Invalid(setOf(MoneyEntryError.CLIENT_REQUIRED)),
            payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, 999, companyId, "5", day)),
        )
        assertEquals(
            SaveMoneyEntryResult.Invalid(setOf(MoneyEntryError.COMPANY_REQUIRED)),
            payments.saveMoneyEntry(MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, clientId, 999, "5", day)),
        )
    }

    @Test
    fun eachCompanySeesOnlyItsOwnMoney() = runTest {
        val other = repos.addCompany("Second")
        save(MoneyEntryKind.MANUAL_CHARGE, 50_000)
        save(MoneyEntryKind.MANUAL_CHARGE, 8_000, company = other)
        save(MoneyEntryKind.CASH_PAYMENT, 3_000, company = other)

        // The default company is shown first.
        assertEquals(Money.ofDram(50_000), clients.observeClientSummary(clientId).first()!!.balance)
        assertEquals(1, payments.observeLedger(clientId).first().size)

        repos.companies.selectCompany(other)
        assertEquals(Money.ofDram(5_000), clients.observeClientSummary(clientId).first()!!.balance)
        assertEquals(2, payments.observeLedger(clientId).first().size)
        assertEquals(Money.ofDram(3_000), payments.observeIncome(day, day).first().cash)
    }
}
