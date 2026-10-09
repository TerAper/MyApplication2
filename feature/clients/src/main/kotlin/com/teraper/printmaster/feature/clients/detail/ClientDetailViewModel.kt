package com.teraper.printmaster.feature.clients.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.PrintersRepository
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.PaymentMethod
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
        /** Recorded calls with this client, newest first. */
        val calls: List<CallRecording> = emptyList(),
        val showAllCalls: Boolean = false,
        val playing: CallRecording? = null,
        /** Clients to move a payment to, while [dialog] is MovePayment. */
        val moveTargets: List<ClientSummary> = emptyList(),
    ) : ClientDetailUiState
}

sealed interface ClientDetailDialog {
    data object ConfirmDelete : ClientDetailDialog
    data object DeleteBlocked : ClientDetailDialog
    data class ConfirmDeleteEntry(val entry: LedgerEntry) : ClientDetailDialog

    /** A bank payment that went to the wrong client: pick the right one. */
    data class MovePayment(val entry: LedgerEntry.Payment, val query: String = "") : ClientDetailDialog
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
    callRecordingsRepository: CallRecordingsRepository,
    private val importRepository: ImportRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    private val clientId: Long = checkNotNull(savedStateHandle[CLIENT_ID_ARG])
    private val dialog = MutableStateFlow<ClientDetailDialog?>(null)
    private val tab = MutableStateFlow(ClientTab.INFO)
    private val deleting = MutableStateFlow(false)
    private val showAllCalls = MutableStateFlow(false)
    private val playing = MutableStateFlow<CallRecording?>(null)

    private val _events = Channel<ClientDetailEvent>(Channel.BUFFERED)
    val events: Flow<ClientDetailEvent> = _events.receiveAsFlow()

    private class Records(
        val summary: ClientSummary?,
        val ledger: List<LedgerEntry>,
        val printers: List<ClientPrinter>,
        val orders: List<Order>,
        val calls: List<CallRecording>,
    )

    private val records = combine(
        clientsRepository.observeClientSummary(clientId),
        paymentsRepository.observeLedger(clientId),
        printersRepository.observeClientPrinters(clientId),
        ordersRepository.observeClientOrders(clientId),
        callRecordingsRepository.observeClientRecordings(clientId),
        ::Records,
    )

    private val callsView = combine(showAllCalls, playing) { all, playing -> all to playing }

    private val dialogView = combine(dialog, clientsRepository.observeClientSummaries()) { dialog, clients ->
        dialog to if (dialog is ClientDetailDialog.MovePayment) {
            clients.filter { it.client.id != clientId && ClientSearch.matches(it.client, dialog.query) }
        } else {
            emptyList()
        }
    }

    val uiState: StateFlow<ClientDetailUiState> = combine(
        records,
        tab,
        dialogView,
        deleting,
        callsView,
    ) { records, tab, (dialog, moveTargets), deleting, (showAll, playing) ->
        val summary = records.summary
        when {
            summary != null -> ClientDetailUiState.Loaded(
                summary, records.ledger, records.printers, records.orders, today, tab, dialog,
                calls = records.calls, showAllCalls = showAll, playing = playing, moveTargets = moveTargets,
            )
            // Just deleted: keep showing Loading for the moment before the screen closes.
            deleting -> ClientDetailUiState.Loading
            else -> ClientDetailUiState.NotFound
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientDetailUiState.Loading)

    fun onTabSelected(tab: ClientTab) = this.tab.update { tab }

    fun onShowAllCalls() = showAllCalls.update { true }

    fun onPlayCall(recording: CallRecording) = playing.update { recording }

    fun onStopCall() = playing.update { null }

    fun onDeleteClick() = dialog.update { ClientDetailDialog.ConfirmDelete }

    fun onEntryClick(entry: LedgerEntry) {
        when {
            entry.canDelete -> dialog.value = ClientDetailDialog.ConfirmDeleteEntry(entry)
            entry is LedgerEntry.Payment && entry.method == PaymentMethod.BANK -> dialog.value = ClientDetailDialog.MovePayment(entry)
        }
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

    fun onMoveQueryChange(query: String) = dialog.update { (it as? ClientDetailDialog.MovePayment)?.copy(query = query) ?: it }

    /** The payment goes to [targetClientId]; its payer is remembered for the next import. */
    fun onMovePayment(targetClientId: Long) {
        val move = dialog.value as? ClientDetailDialog.MovePayment ?: return
        dialog.value = null
        viewModelScope.launch { importRepository.assign(move.entry.id, targetClientId) }
    }

    fun onConfirmDeleteEntry(entry: LedgerEntry) {
        dialog.value = null
        viewModelScope.launch { paymentsRepository.deleteEntry(entry) }
    }
}
