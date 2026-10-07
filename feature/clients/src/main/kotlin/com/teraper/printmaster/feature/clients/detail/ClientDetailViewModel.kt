package com.teraper.printmaster.feature.clients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.PrintersRepository
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class ClientTab { INFO, PRINTERS, ORDERS, FINANCE }

sealed interface ClientDetailUiState {
    data object Loading : ClientDetailUiState
    data object NotFound : ClientDetailUiState
    data class Loaded(
        val summary: ClientSummary,
        val ledger: List<LedgerEntry> = emptyList(),
        val printers: List<ClientPrinter> = emptyList(),
        val orders: List<Order> = emptyList(),
        val today: LocalDate? = null,
        val tab: ClientTab = ClientTab.INFO,
        val dialog: ClientDetailDialog? = null,
    ) : ClientDetailUiState
}

sealed interface ClientDetailDialog {
    data object ConfirmDelete : ClientDetailDialog
    data object DeleteBlocked : ClientDetailDialog
    data class ConfirmDeleteEntry(val entry: LedgerEntry) : ClientDetailDialog
}

sealed interface ClientDetailEvent {
    data object Deleted : ClientDetailEvent
}

@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientsRepository: ClientsRepository,
    private val paymentsRepository: PaymentsRepository,
    printersRepository: PrintersRepository,
    ordersRepository: OrdersRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    private val clientId: Long = checkNotNull(savedStateHandle[CLIENT_ID_ARG])
    private val dialog = MutableStateFlow<ClientDetailDialog?>(null)
    private val tab = MutableStateFlow(ClientTab.INFO)
    private val deleting = MutableStateFlow(false)

    private val _events = Channel<ClientDetailEvent>(Channel.BUFFERED)
    val events: Flow<ClientDetailEvent> = _events.receiveAsFlow()

    private class Records(
        val summary: ClientSummary?,
        val ledger: List<LedgerEntry>,
        val printers: List<ClientPrinter>,
        val orders: List<Order>,
    )

    private val records = combine(
        clientsRepository.observeClientSummary(clientId),
        paymentsRepository.observeLedger(clientId),
        printersRepository.observeClientPrinters(clientId),
        ordersRepository.observeClientOrders(clientId),
        ::Records,
    )

    val uiState: StateFlow<ClientDetailUiState> = combine(
        records,
        tab,
        dialog,
        deleting,
    ) { records, tab, dialog, deleting ->
        val summary = records.summary
        when {
            summary != null -> ClientDetailUiState.Loaded(summary, records.ledger, records.printers, records.orders, today, tab, dialog)
            // Just deleted: keep showing Loading for the moment before the screen closes.
            deleting -> ClientDetailUiState.Loading
            else -> ClientDetailUiState.NotFound
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientDetailUiState.Loading)

    fun onTabSelected(tab: ClientTab) = this.tab.update { tab }

    fun onDeleteClick() = dialog.update { ClientDetailDialog.ConfirmDelete }

    fun onEntryClick(entry: LedgerEntry) {
        if (entry.canDelete) dialog.value = ClientDetailDialog.ConfirmDeleteEntry(entry)
    }

    fun onDismissDialog() = dialog.update { null }

    fun onConfirmDelete() {
        dialog.value = null
        viewModelScope.launch {
            deleting.value = true
            when (clientsRepository.deleteClient(clientId)) {
                DeleteClientResult.DELETED, DeleteClientResult.NOT_FOUND -> _events.send(ClientDetailEvent.Deleted)
                DeleteClientResult.HAS_RECORDS -> {
                    deleting.value = false
                    dialog.value = ClientDetailDialog.DeleteBlocked
                }
            }
        }
    }

    fun onConfirmDeleteEntry(entry: LedgerEntry) {
        dialog.value = null
        viewModelScope.launch { paymentsRepository.deleteEntry(entry) }
    }
}
