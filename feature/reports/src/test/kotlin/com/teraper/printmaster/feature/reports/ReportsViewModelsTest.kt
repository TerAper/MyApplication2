package com.teraper.printmaster.feature.reports

import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DebtReportLabels
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.ExportRepository
import com.teraper.printmaster.core.data.repository.ReportsRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.core.model.MonthReport
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.reports.export.DebtExportDialog
import com.teraper.printmaster.feature.reports.export.DebtExportEvent
import com.teraper.printmaster.feature.reports.export.DebtExportScope
import com.teraper.printmaster.feature.reports.export.DebtExportViewModel
import com.teraper.printmaster.feature.reports.overview.ReportsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)
    private val october = YearMonth.of(2026, 10)

    private fun summary(id: Long, name: String, charged: Long, paid: Long) = ClientSummary(
        Client(id, name, ClientType.FIRM, null, "", emptyList(), emptyList()),
        charged = Money.ofDram(charged),
        paid = Money.ofDram(paid),
    )

    private val clients = object : ClientsRepository {
        val list = MutableStateFlow(
            listOf(
                summary(1, "Small", 10_000, 4_000),
                summary(2, "Big", 100_000, 0),
                summary(3, "Settled", 5_000, 5_000),
            ),
        )
        override fun observeClientSummaries() = list
        override fun observeClientSummary(id: Long) = list.map { l -> l.firstOrNull { it.client.id == id } }
        override suspend fun saveClient(draft: ClientDraft) = SaveClientResult.Saved(0)
        override suspend fun deleteClient(id: Long) = DeleteClientResult.DELETED
    }

    private val reports = object : ReportsRepository {
        val asked = mutableListOf<YearMonth>()
        override fun observeMonth(month: YearMonth): Flow<MonthReport> {
            asked += month
            return flowOf(MonthReport(month, IncomeTotals(cash = Money.ofDram(month.monthValue.toLong()))))
        }
        override fun observeIncomeHistory(last: YearMonth, count: Int) =
            flowOf((count - 1 downTo 0).map { MonthIncome(last.minusMonths(it.toLong()), IncomeTotals()) })
    }

    @Test
    fun reportsShowMonthAndBiggestDebtors() = runTest {
        val vm = ReportsViewModel(reports, clients, FakeCompaniesRepository(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(october, state.month)
        assertFalse(state.canGoNext)
        assertEquals(6, state.history.size)
        assertEquals(listOf("Big", "Small"), state.topDebtors.map { it.client.name })
        assertEquals(Money.ofDram(106_000), state.totalDebt)

        vm.onNextMonth()
        assertEquals(october, vm.uiState.value.month)
        vm.onPreviousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertEquals(Money.ofDram(9), vm.uiState.value.report.income.cash)
        assertTrue(vm.uiState.value.canGoNext)
        vm.onMonthClick(YearMonth.of(2027, 1))
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
    }

    @Test
    fun exportSavesOrSharesTheChosenClients() = runTest {
        val export = object : ExportRepository {
            val calls = mutableListOf<String>()
            var fail = false
            override suspend fun debtReportFileName() = "Debts-Main-2026-10-09.xlsx"
            override suspend fun saveDebtReport(uri: String, onlyDebtors: Boolean, labels: DebtReportLabels): Boolean {
                calls += "save $uri $onlyDebtors"
                return !fail
            }
            override suspend fun cacheDebtReport(onlyDebtors: Boolean, labels: DebtReportLabels): String? {
                calls += "cache $onlyDebtors"
                return if (fail) null else "/cache/exports/x.xlsx"
            }
        }
        val labels = DebtReportLabels("Debts", "Client", "Tax ID", "Phone", "Charged", "Paid", "Owes", "Last", "Total")
        val vm = DebtExportViewModel(clients, FakeCompaniesRepository(), export)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(2, vm.uiState.value.rowCount)
        vm.onScopeChange(DebtExportScope.ALL)
        assertEquals(3, vm.uiState.value.rowCount)

        vm.onSaveClick()
        assertEquals(DebtExportEvent.PickSaveLocation("Debts-Main-2026-10-09.xlsx"), vm.events.first())
        vm.onSaveTo("content://x", labels)
        assertEquals(DebtExportDialog.SAVED, vm.uiState.value.dialog)

        vm.onDismissDialog()
        vm.onScopeChange(DebtExportScope.DEBTORS)
        vm.onShare(labels)
        assertEquals(DebtExportEvent.Share("/cache/exports/x.xlsx"), vm.events.first())
        assertEquals(listOf("save content://x false", "cache true"), export.calls)

        export.fail = true
        vm.onShare(labels)
        assertEquals(DebtExportDialog.FAILED, vm.uiState.value.dialog)
    }
}
