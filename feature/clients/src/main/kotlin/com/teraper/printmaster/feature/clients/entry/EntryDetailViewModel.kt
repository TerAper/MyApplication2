package com.teraper.printmaster.feature.clients.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.EntryKind
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.feature.clients.navigation.ENTRY_ID_ARG
import com.teraper.printmaster.feature.clients.navigation.IS_CHARGE_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

sealed interface EntryDetailUiState {
    data object Loading : EntryDetailUiState
    data object NotFound : EntryDetailUiState
    data class Loaded(
        val detail: ImportedEntryDetail,
        /** Search text while picking the right client; null = picker closed. */
        val pickQuery: String? = null,
        val pickClients: List<ClientSummary> = emptyList(),
        val confirmDetach: Boolean = false,
        val confirmDelete: Boolean = false,
    ) : EntryDetailUiState
}

sealed interface EntryDetailEvent {
    data object Deleted : EntryDetailEvent
}

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importRepository: ImportRepository,
    private val paymentsRepository: PaymentsRepository,
    clientsRepository: ClientsRepository,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle[ENTRY_ID_ARG])
    private val isCharge: Boolean = savedStateHandle[IS_CHARGE_ARG] ?: false
    private val pickQuery = MutableStateFlow<String?>(null)
    private val confirmDetach = MutableStateFlow(false)
    private val confirmDelete = MutableStateFlow(false)

    private val _events = Channel<EntryDetailEvent>(Channel.BUFFERED)
    val events: Flow<EntryDetailEvent> = _events.receiveAsFlow()

    private val dialogs = combine(confirmDetach, confirmDelete) { detach, delete -> detach to delete }

    val uiState: StateFlow<EntryDetailUiState> = combine(
        if (isCharge) importRepository.observeChargeDetail(id) else importRepository.observePaymentDetail(id),
        clientsRepository.observeClientSummaries(),
        pickQuery,
        dialogs,
    ) { detail, clients, query, (detach, delete) ->
        if (detail == null) {
            EntryDetailUiState.NotFound
        } else {
            EntryDetailUiState.Loaded(
                detail = detail,
                pickQuery = query,
                pickClients = query?.let { q -> clients.filter { it.client.id != detail.clientId && ClientSearch.matches(it.client, q) } }.orEmpty(),
                confirmDetach = detach,
                confirmDelete = delete,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUiState.Loading)

    fun onMoveClick() {
        pickQuery.value = ""
    }

    fun onPickQueryChange(query: String) = pickQuery.update { if (it == null) null else query }

    fun onDismissPick() {
        pickQuery.value = null
    }

    /** Moves the entry; for a payment the payer is remembered as this client's and never as the old one's. */
    fun onClientPicked(clientId: Long) {
        pickQuery.value = null
        viewModelScope.launch {
            if (isCharge) importRepository.moveInvoice(id, clientId) else importRepository.assign(id, clientId)
        }
    }

    fun onDetachClick() {
        confirmDetach.value = true
    }

    fun onDismissDetach() {
        confirmDetach.value = false
    }

    fun onConfirmDetach() {
        confirmDetach.value = false
        viewModelScope.launch { importRepository.detachPayment(id) }
    }

    fun onDeleteClick() {
        confirmDelete.value = true
    }

    fun onDismissDelete() {
        confirmDelete.value = false
    }

    /** Only cash payments and debts typed on the phone can be deleted. */
    fun onConfirmDelete() {
        confirmDelete.value = false
        val detail = (uiState.value as? EntryDetailUiState.Loaded)?.detail?.takeIf { it.canDelete } ?: return
        val entry = if (isCharge) {
            LedgerEntry.Charge(detail.id, detail.date, detail.amount, detail.note, 0, ChargeSource.MANUAL, detail.documentNumber)
        } else {
            LedgerEntry.Payment(detail.id, detail.date, detail.amount, detail.note, 0, PaymentMethod.CASH, detail.documentNumber)
        }
        viewModelScope.launch {
            if (paymentsRepository.deleteEntry(entry)) _events.send(EntryDetailEvent.Deleted)
        }
    }
}
