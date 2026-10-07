package com.teraper.printmaster.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val visits: List<Order> = emptyList(),
    val overdue: List<Order> = emptyList(),
    /** What clients owe the company being viewed. */
    val toCollect: Money = Money.ZERO,
    val debtorCount: Int = 0,
    val receivedToday: IncomeTotals = IncomeTotals(),
) {
    val openVisits: Int get() = visits.count { it.isOpen }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    ordersRepository: OrdersRepository,
    clientsRepository: ClientsRepository,
    paymentsRepository: PaymentsRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val uiState: StateFlow<TodayUiState> = combine(
        ordersRepository.observeOrdersOn(today),
        ordersRepository.observeOverdue(today),
        clientsRepository.observeClientSummaries(),
        paymentsRepository.observeIncome(today, today),
    ) { visits, overdue, clients, income ->
        val debts = clients.map { it.balance }.filter { it.isPositive }
        TodayUiState(
            isLoading = false,
            today = today,
            visits = visits,
            overdue = overdue,
            toCollect = debts.sum(),
            debtorCount = debts.size,
            receivedToday = income,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(today = today))
}
