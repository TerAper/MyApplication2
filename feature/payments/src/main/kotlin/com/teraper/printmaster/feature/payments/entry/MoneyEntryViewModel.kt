package com.teraper.printmaster.feature.payments.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.PaymentsRepository
import com.teraper.printmaster.core.data.repository.SaveMoneyEntryResult
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.feature.payments.navigation.CLIENT_ID_ARG
import com.teraper.printmaster.feature.payments.navigation.IS_CHARGE_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MoneyEntryForm(
    val draft: MoneyEntryDraft,
    val errors: Set<MoneyEntryError> = emptySet(),
    val isSaving: Boolean = false,
    val showClientPicker: Boolean = false,
    val showDatePicker: Boolean = false,
)

data class MoneyEntryUiState(
    val form: MoneyEntryForm,
    val today: LocalDate,
    /** The chosen client with their current balance; null until one is picked. */
    val client: ClientSummary? = null,
) {
    val draft get() = form.draft
    val isPayment get() = draft.kind == MoneyEntryKind.CASH_PAYMENT

    /** What the client will owe after saving (negative = overpaid). */
    val balanceAfter: Money?
        get() = client?.let { if (isPayment) it.balance - draft.amount else it.balance + draft.amount }
}

sealed interface MoneyEntryEvent {
    data object Saved : MoneyEntryEvent
}

@HiltViewModel
class MoneyEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    clientsRepository: ClientsRepository,
    private val paymentsRepository: PaymentsRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val kind = if (savedStateHandle.get<Boolean>(IS_CHARGE_ARG) == true) MoneyEntryKind.MANUAL_CHARGE else MoneyEntryKind.CASH_PAYMENT
    private val presetClientId = savedStateHandle.get<Long>(CLIENT_ID_ARG)?.takeIf { it != 0L }

    private val form = MutableStateFlow(
        MoneyEntryForm(
            draft = MoneyEntryDraft(kind = kind, clientId = presetClientId, date = today),
            // No client given → open the picker straight away.
            showClientPicker = presetClientId == null,
        ),
    )

    private val allClients = clientsRepository.observeClientSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MoneyEntryUiState> = combine(form, allClients) { form, clients ->
        MoneyEntryUiState(form = form, today = today, client = clients.firstOrNull { it.client.id == form.draft.clientId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyEntryUiState(form.value, today))

    // Picker search kept separate so typing stays instant.
    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    /** Clients for the picker; debtors first when paying, since they're the likely payers. */
    val pickerClients: StateFlow<List<ClientSummary>> = combine(allClients, _pickerQuery) { clients, query ->
        val found = clients.filter { ClientSearch.matches(it.client, query) }
        if (kind == MoneyEntryKind.CASH_PAYMENT) found.sortedByDescending { it.balance.isPositive } else found
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<MoneyEntryEvent>(Channel.BUFFERED)
    val events: Flow<MoneyEntryEvent> = _events.receiveAsFlow()

    fun onKey(key: String) = editDraft { it.typed(key) }

    fun onUseAmount(amount: Money) = editDraft { it.withAmount(amount) }

    fun onNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onOpenClientPicker() = form.update { it.copy(showClientPicker = true) }

    fun onPickerQueryChange(query: String) {
        _pickerQuery.value = query
    }

    fun onClientPicked(clientId: Long) {
        _pickerQuery.value = ""
        form.update { it.copy(showClientPicker = false) }
        editDraft { it.copy(clientId = clientId) }
    }

    fun onDismissClientPicker() = form.update { it.copy(showClientPicker = false) }

    fun onOpenDatePicker() = form.update { it.copy(showDatePicker = true) }

    fun onDatePicked(date: LocalDate?) {
        form.update { it.copy(showDatePicker = false) }
        if (date != null) editDraft { it.copy(date = date) }
    }

    fun onSave() {
        val current = form.value
        if (current.isSaving) return
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = paymentsRepository.saveMoneyEntry(current.draft)) {
                is SaveMoneyEntryResult.Saved -> _events.send(MoneyEntryEvent.Saved)
                is SaveMoneyEntryResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
            }
        }
    }

    private fun editDraft(change: (MoneyEntryDraft) -> MoneyEntryDraft) = form.update { f ->
        val draft = change(f.draft)
        // Once an error is shown, it clears as soon as it's fixed.
        f.copy(draft = draft, errors = f.errors.intersect(draft.validate()))
    }
}
