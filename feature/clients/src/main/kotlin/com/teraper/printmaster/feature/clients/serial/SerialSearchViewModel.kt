package com.teraper.printmaster.feature.clients.serial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.model.SerialNumbers
import com.teraper.printmaster.feature.clients.navigation.SERIAL_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** A client item with this serial and the orders with work on it. */
data class SerialHit(val match: SerialMatch, val orders: List<Order>)

data class SerialSearchUiState(
    val isSearching: Boolean = true,
    val hits: List<SerialHit> = emptyList(),
    val today: LocalDate,
    /** The phone can't scan (no Google Play services). */
    val scanUnavailable: Boolean = false,
)

/** "Whose is this?": by its scanned or typed serial number. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SerialSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientItems: ClientItemsRepository,
    private val orders: OrdersRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val _query = MutableStateFlow(SerialNumbers.clean(savedStateHandle[SERIAL_ARG] ?: ""))
    val query: StateFlow<String> = _query
    private val scanUnavailable = MutableStateFlow(false)

    private val hits = _query.map(SerialNumbers::key).distinctUntilChanged().debounce(250).flatMapLatest { key ->
        if (key.isEmpty()) {
            flowOf(emptyList())
        } else {
            flow { emit(clientItems.findBySerial(key)) }.flatMapLatest { matches ->
                if (matches.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(matches.map { m -> orders.observeClientItemOrders(m.item.id).map { SerialHit(m, it) } }) { it.toList() }
                }
            }
        }
    }

    val uiState: StateFlow<SerialSearchUiState> = combine(hits, scanUnavailable) { hits, unavailable ->
        SerialSearchUiState(isSearching = false, hits = hits, today = today, scanUnavailable = unavailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SerialSearchUiState(today = today))

    fun onQueryChange(text: String) = _query.update { text }

    fun onScanned(text: String) {
        scanUnavailable.value = false
        _query.value = SerialNumbers.clean(text)
    }

    fun onScanUnavailable() = scanUnavailable.update { true }
}
