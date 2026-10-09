package com.teraper.printmaster.feature.payments

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveMoneyEntryResult
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeImportRepository
import com.teraper.printmaster.feature.payments.entry.MoneyEntryEvent
import com.teraper.printmaster.feature.payments.entry.MoneyEntryViewModel
import com.teraper.printmaster.feature.payments.overview.BalanceFilter
import com.teraper.printmaster.feature.payments.overview.PaymentsOverviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModelsTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 7)

    private fun summary(id: Long, name: String, charged: Long, paid: Long) = ClientSummary(
        client = Client(id, name, ClientType.FIRM, null, "", emptyList(), emptyList()),
        charged = Money.ofDram(charged),
        paid = Money.ofDram(paid),
    )

    private val clients = object : ClientsRepository {
        val list = MutableStateFlow(
            listOf(
                summary(1, "Small debt", 10_000, 4_000),
                summary(2, "Big debt", 100_000, 0),
                summary(3, "Settled", 5_000, 5_000),
                summary(4, "Overpaid", 0, 2_000),
                summary(5, "New", 0, 0),
            ),
        )
        override fun observeClientSummaries() = list
        override fun observeClientSummary(id: Long) = list.map { l -> l.firstOrNull { it.client.id == id } }
        override suspend fun saveClient(draft: ClientDraft) = SaveClientResult.Saved(0)
        override suspend fun deleteClient(id: Long) = DeleteClientResult.DELETED
    }

    private val companies = FakeCompaniesRepository(listOf(Company(1, "Main"), Company(2, "Second")))

    private val payments = object : PaymentsRepository {
        val saved = mutableListOf<MoneyEntryDraft>()
        val income = MutableStateFlow(IncomeTotals(cash = Money.ofDram(3_000), bank = Money.ofDram(7_000)))
        var incomeRange: Pair<LocalDate, LocalDate>? = null
        override fun observeLedger(clientId: Long): Flow<List<LedgerEntry>> = MutableStateFlow(emptyList())
        override fun observeIncome(from: LocalDate, to: LocalDate): Flow<IncomeTotals> { incomeRange = from to to; return income }
        override suspend fun saveMoneyEntry(draft: MoneyEntryDraft): SaveMoneyEntryResult {
            val errors = draft.validate()
            if (errors.isNotEmpty()) return SaveMoneyEntryResult.Invalid(errors)
            saved += draft
            return SaveMoneyEntryResult.Saved(1)
        }
        override suspend fun deleteEntry(entry: LedgerEntry) = true
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collect(flow: Flow<*>) = backgroundScope.launch(UnconfinedTestDispatcher()) { flow.collect {} }

    @Test
    fun overviewTotalsAndFilters() = runTest {
        val vm = PaymentsOverviewViewModel(clients, payments, FakeImportRepository(), clock)
        collect(vm.uiState)
        val state = vm.uiState.value

        assertEquals(Money.ofDram(106_000), state.totalDebt) // overpayments aren't subtracted
        assertEquals(2, state.debtorCount)
        assertEquals(Money.ofDram(10_000), state.incomeThisMonth.total)
        assertEquals(LocalDate.of(2026, 10, 1) to today, payments.incomeRange)
        assertEquals(listOf("Big debt", "Small debt"), state.rows.map { it.client.name })

        vm.onFilterChange(BalanceFilter.PAID)
        assertEquals(listOf("Settled"), vm.uiState.value.rows.map { it.client.name })
        vm.onFilterChange(BalanceFilter.OVERPAID)
        assertEquals(listOf("Overpaid"), vm.uiState.value.rows.map { it.client.name })
        vm.onFilterChange(BalanceFilter.ALL)
        assertEquals(5, vm.uiState.value.rows.size)
    }

    private fun entryVm(clientId: Long = 0, isCharge: Boolean = false) = MoneyEntryViewModel(
        SavedStateHandle(mapOf("clientId" to clientId, "isCharge" to isCharge)), clients, payments, companies, clock,
    )

    @Test
    fun cashPaymentForPresetClient() = runTest {
        val vm = entryVm(clientId = 2)
        collect(vm.uiState)
        assertFalse(vm.uiState.value.form.showClientPicker)
        assertEquals("Big debt", vm.uiState.value.client?.client?.name)

        listOf("3", "000", "0").forEach(vm::onKey)
        assertEquals(Money.ofDram(70_000), vm.uiState.value.balanceAfter)

        vm.onUseAmount(Money.ofDram(100_000))
        assertTrue(vm.uiState.value.balanceAfter!!.isZero)

        vm.onSave()
        assertEquals(MoneyEntryEvent.Saved, vm.events.first())
        assertEquals(
            MoneyEntryDraft(MoneyEntryKind.CASH_PAYMENT, clientId = 2, companyId = 1, amountDigits = "100000", date = today),
            payments.saved.single(),
        )
    }

    @Test
    fun cashGoesUnderTheCompanyPickedOnTheForm() = runTest {
        val vm = entryVm(clientId = 2)
        collect(vm.uiState)
        assertEquals("Main", vm.uiState.value.company?.name)

        vm.onCompanySelected(2)
        assertEquals("Second", vm.uiState.value.company?.name)
        vm.onKey("5")
        vm.onSave()
        assertEquals(MoneyEntryEvent.Saved, vm.events.first())
        assertEquals(2L, payments.saved.single().companyId)
    }

    @Test
    fun withoutClientThePickerOpensAndSaveIsBlocked() = runTest {
        val vm = entryVm()
        collect(vm.uiState); collect(vm.pickerClients)
        assertTrue(vm.uiState.value.form.showClientPicker)
        assertNull(vm.uiState.value.client)
        // Debtors are listed first when taking a payment.
        assertEquals(listOf("Small debt", "Big debt"), vm.pickerClients.value.take(2).map { it.client.name })

        vm.onPickerQueryChange("settled")
        assertEquals(listOf("Settled"), vm.pickerClients.value.map { it.client.name })

        vm.onDismissClientPicker()
        vm.onSave()
        assertEquals(setOf(MoneyEntryError.CLIENT_REQUIRED, MoneyEntryError.AMOUNT_REQUIRED), vm.uiState.value.form.errors)

        vm.onClientPicked(3)
        assertEquals(setOf(MoneyEntryError.AMOUNT_REQUIRED), vm.uiState.value.form.errors)
    }

    @Test
    fun manualChargeRaisesDebtAndKeepsChosenDate() = runTest {
        val vm = entryVm(clientId = 5, isCharge = true)
        collect(vm.uiState)
        vm.onKey("5"); vm.onKey("000")
        vm.onDatePicked(LocalDate.of(2025, 12, 31))
        assertEquals(Money.ofDram(5_000), vm.uiState.value.balanceAfter)

        vm.onSave()
        assertEquals(MoneyEntryEvent.Saved, vm.events.first())
        assertEquals(MoneyEntryKind.MANUAL_CHARGE, payments.saved.single().kind)
        assertEquals(LocalDate.of(2025, 12, 31), payments.saved.single().date)
    }
}
