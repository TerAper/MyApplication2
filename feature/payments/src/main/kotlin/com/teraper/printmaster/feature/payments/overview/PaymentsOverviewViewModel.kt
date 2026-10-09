package com.teraper.printmaster.feature.payments.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class BalanceFilter {
    IN_DEBT, PAID, OVERPAID, ALL;

    fun accepts(s: ClientSummary): Boolean = when (this) {
        IN_DEBT -> s.balance.isPositive
        // Settled clients who had at least something charged or paid.
        PAID -> s.balance.isZero && (s.charged.isPositive || s.paid.isPositive)
        OVERPAID -> s.balance.isNegative
        ALL -> true
    }
}

data class PaymentsOverviewUiState(
    val isLoading: Boolean = true,
    /** Sum of what debtors owe (overpayments are not subtracted). */
    val totalDebt: Money = Money.ZERO,
    val debtorCount: Int = 0,
    val incomeThisMonth: IncomeTotals = IncomeTotals(),
    val filter: BalanceFilter = BalanceFilter.IN_DEBT,
    val rows: List<ClientSummary> = emptyList(),
    val hasClients: Boolean = false,
    /** Imported bank payments waiting for the user to say whose they are. */
    val pendingPayments: Int = 0,
)

@HiltViewModel
class PaymentsOverviewViewModel @Inject constructor(
    clientsRepository: ClientsRepository,
    paymentsRepository: PaymentsRepository,
    importRepository: ImportRepository,
    clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(BalanceFilter.IN_DEBT)
    private val today = LocalDate.now(clock)

    val uiState: StateFlow<PaymentsOverviewUiState> = combine(
        clientsRepository.observeClientSummaries(),
        paymentsRepository.observeIncome(today.withDayOfMonth(1), today),
        filter,
        importRepository.observePending(),
    ) { clients, income, filter, pending ->
        val debtors = clients.filter { it.balance.isPositive }
        PaymentsOverviewUiState(
            isLoading = false,
            totalDebt = debtors.map { it.balance }.sum(),
            debtorCount = debtors.size,
            incomeThisMonth = income,
            filter = filter,
            rows = clients.filter { filter.accepts(it) }.let { rows ->
                // Biggest debts first; the other lists stay alphabetical.
                if (filter == BalanceFilter.IN_DEBT) rows.sortedByDescending { it.balance } else rows
            },
            hasClients = clients.isNotEmpty(),
            pendingPayments = pending.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsOverviewUiState())

    fun onFilterChange(filter: BalanceFilter) {
        this.filter.value = filter
    }
}
