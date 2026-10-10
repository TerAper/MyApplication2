package com.teraper.printmaster.feature.expenses.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ExpensesRepository
import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseTotals
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val month: YearMonth,
    val canGoNext: Boolean = false,
    /** Newest day first. */
    val days: List<Pair<LocalDate, List<Expense>>> = emptyList(),
    val totals: ExpenseTotals = ExpenseTotals(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(repository: ExpensesRepository, clock: Clock) : ViewModel() {

    private val current = YearMonth.now(clock)
    private val month = MutableStateFlow(current)

    val uiState: StateFlow<ExpensesUiState> = month.flatMapLatest { m ->
        combine(repository.observeExpenses(m), repository.observeTotals(m)) { expenses, totals ->
            ExpensesUiState(
                isLoading = false,
                month = m,
                canGoNext = m < current,
                days = expenses.groupBy { it.date }.toList().sortedByDescending { it.first },
                totals = totals,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState(month = current))

    fun onPreviousMonth() = month.update { it.minusMonths(1) }

    fun onNextMonth() = month.update { if (it < current) it.plusMonths(1) else it }
}
