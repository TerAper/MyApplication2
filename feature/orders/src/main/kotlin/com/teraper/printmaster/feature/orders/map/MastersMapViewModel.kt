package com.teraper.printmaster.feature.orders.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.tracking.TrackingRepository
import com.teraper.printmaster.core.model.MasterTrack
import com.teraper.printmaster.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MastersMapUiState(val isLoading: Boolean = true, val tracks: List<MasterTrack> = emptyList())

/** Company's phone: every master sharing where he is on today's (or overdue) open orders. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MastersMapViewModel @Inject constructor(
    ordersRepository: OrdersRepository,
    tracking: TrackingRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val uiState: StateFlow<MastersMapUiState> = followedOrders(ordersRepository, today)
        .map { orders -> orders.map { it.id to (it.masterName.orEmpty() to it.clientName) } }
        .distinctUntilChanged()
        .flatMapLatest { orders ->
            if (orders.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(orders.map { (id, names) -> tracking.observeWay(id).map { MasterTrack(id, names.first, names.second, it) } }) { it.toList() }
            }
        }
        .map { MastersMapUiState(false, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MastersMapUiState())
}

/** Today's and overdue open orders whose master shares where he is with this company. */
internal fun followedOrders(orders: OrdersRepository, today: LocalDate): Flow<List<Order>> =
    combine(orders.observeOrdersOn(today), orders.observeOverdue(today)) { day, overdue ->
        (overdue + day).filter { !it.fromAttachedCompany && it.locationLive }.distinctBy { it.id }
    }
