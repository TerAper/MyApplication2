package com.teraper.printmaster.feature.orders.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class OrdersUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val selectedDate: LocalDate,
    /** The day strip: a week back, three weeks ahead. */
    val days: List<LocalDate> = emptyList(),
    val counts: Map<LocalDate, Int> = emptyMap(),
    val orders: List<Order> = emptyList(),
    /** Unfinished orders from earlier days; shown on today only. */
    val overdue: List<Order> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrdersViewModel @Inject constructor(
    ordersRepository: OrdersRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val days = (-DAYS_BACK..DAYS_AHEAD).map { today.plusDays(it.toLong()) }
    private val selected = MutableStateFlow(today)

    val uiState: StateFlow<OrdersUiState> = combine(
        selected,
        selected.flatMapLatest { ordersRepository.observeOrdersOn(it) },
        selected.flatMapLatest { if (it == today) ordersRepository.observeOverdue(today) else flowOf(emptyList()) },
        ordersRepository.observeDayCounts(days.first(), days.last()),
    ) { date, orders, overdue, counts ->
        OrdersUiState(false, today, date, days, counts, orders, overdue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState(today = today, selectedDate = today, days = days))

    fun onDateSelected(date: LocalDate) {
        selected.value = date
    }

    private companion object {
        const val DAYS_BACK = 7
        const val DAYS_AHEAD = 21
    }
}
