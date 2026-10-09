package com.teraper.printmaster.feature.reports.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.ReportsRepository
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.core.model.MonthReport
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class ReportsUiState(
    val isLoading: Boolean = true,
    val company: Company? = null,
    val month: YearMonth,
    /** The chart ends at the current month; no reports for the future. */
    val canGoNext: Boolean = false,
    val report: MonthReport = MonthReport(month),
    /** The last [CHART_MONTHS] months up to the current one, oldest first. */
    val history: List<MonthIncome> = emptyList(),
    val totalDebt: Money = Money.ZERO,
    val debtorCount: Int = 0,
    val topDebtors: List<ClientSummary> = emptyList(),
)

internal const val CHART_MONTHS = 6
private const val TOP_DEBTORS = 5

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    reportsRepository: ReportsRepository,
    clientsRepository: ClientsRepository,
    companiesRepository: CompaniesRepository,
    clock: Clock,
) : ViewModel() {

    private val currentMonth = YearMonth.now(clock)
    private val month = MutableStateFlow(currentMonth)

    val uiState: StateFlow<ReportsUiState> = combine(
        month.flatMapLatest { reportsRepository.observeMonth(it) },
        reportsRepository.observeIncomeHistory(currentMonth, CHART_MONTHS),
        clientsRepository.observeClientSummaries(),
        companiesRepository.observeActiveCompany(),
    ) { report, history, clients, company ->
        val debtors = clients.filter { it.balance.isPositive }.sortedByDescending { it.balance }
        ReportsUiState(
            isLoading = false,
            company = company,
            month = report.month,
            canGoNext = report.month < currentMonth,
            report = report,
            history = history,
            totalDebt = debtors.map { it.balance }.sum(),
            debtorCount = debtors.size,
            topDebtors = debtors.take(TOP_DEBTORS),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState(month = currentMonth))

    fun onPreviousMonth() = month.update { it.minusMonths(1) }

    fun onNextMonth() = month.update { if (it < currentMonth) it.plusMonths(1) else it }

    fun onMonthClick(month: YearMonth) {
        if (month <= currentMonth) this.month.value = month
    }
}
