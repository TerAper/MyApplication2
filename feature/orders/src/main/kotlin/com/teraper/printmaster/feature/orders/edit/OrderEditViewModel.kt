package com.teraper.printmaster.feature.orders.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.feature.orders.navigation.CLIENT_ID_ARG
import com.teraper.printmaster.feature.orders.navigation.DATE_ARG
import com.teraper.printmaster.feature.orders.navigation.NO_DATE
import com.teraper.printmaster.feature.orders.navigation.ORDER_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class OrderForm(
    val isLoading: Boolean = false,
    val draft: OrderDraft,
    val errors: Set<OrderDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val showClientPicker: Boolean = false,
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val showDiscardDialog: Boolean = false,
)

data class OrderEditUiState(
    val form: OrderForm,
    val today: LocalDate,
    /** The chosen client (with debt), for addresses and phones. */
    val client: ClientSummary? = null,
    /** Shown as choices only when there is more than one. */
    val masters: List<Master> = emptyList(),
    /** New orders go to this company. */
    val company: Company? = null,
) {
    val draft: OrderDraft get() = form.draft
    val isNew: Boolean get() = draft.isNew
}

sealed interface OrderEditEvent {
    data class Saved(val orderId: Long, val wasNew: Boolean) : OrderEditEvent
    data object Close : OrderEditEvent
}

@HiltViewModel
class OrderEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    clientsRepository: ClientsRepository,
    companiesRepository: CompaniesRepository,
    private val ordersRepository: OrdersRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val orderId: Long = savedStateHandle[ORDER_ID_ARG] ?: 0L
    private val presetClientId: Long? = savedStateHandle.get<Long>(CLIENT_ID_ARG)?.takeIf { it != 0L }
    private val presetDate: LocalDate = savedStateHandle.get<Long>(DATE_ARG)?.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay) ?: today

    private var initialDraft = OrderDraft(date = presetDate)
    private var triedToSave = false

    private val form = MutableStateFlow(OrderForm(isLoading = true, draft = initialDraft))

    private val allClients = clientsRepository.observeClientSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<OrderEditUiState> = combine(
        form,
        allClients,
        companiesRepository.observeMasters(),
        companiesRepository.observeActiveCompany(),
    ) { form, clients, masters, company ->
        OrderEditUiState(
            form = form,
            today = today,
            client = clients.firstOrNull { it.client.id == form.draft.clientId },
            masters = masters,
            company = company,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderEditUiState(form.value, today))

    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    val pickerClients: StateFlow<List<ClientSummary>> = combine(allClients, _pickerQuery) { clients, query ->
        clients.filter { ClientSearch.matches(it.client, query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<OrderEditEvent>(Channel.BUFFERED)
    val events: Flow<OrderEditEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val masters = companiesRepository.observeMasters().first()
            if (orderId != 0L) {
                val order = ordersRepository.observeOrder(orderId).first()
                if (order == null) {
                    _events.send(OrderEditEvent.Close)
                    return@launch
                }
                initialDraft = OrderDraft.from(order)
            } else {
                // One master (the usual MASTER account): they do every job.
                var draft = OrderDraft(date = presetDate, masterId = masters.singleOrNull()?.id)
                if (presetClientId != null) {
                    clientsRepository.observeClientSummary(presetClientId).first()?.let { draft = draft.withClient(it.client) }
                }
                initialDraft = draft
            }
            form.update {
                it.copy(isLoading = false, draft = initialDraft, showClientPicker = orderId == 0L && initialDraft.clientId == null)
            }
        }
    }

    fun onOpenClientPicker() = form.update { it.copy(showClientPicker = true) }

    fun onPickerQueryChange(query: String) {
        _pickerQuery.value = query
    }

    fun onClientPicked(clientId: Long) {
        _pickerQuery.value = ""
        form.update { it.copy(showClientPicker = false) }
        val client = allClients.value.firstOrNull { it.client.id == clientId }?.client ?: return
        editDraft { it.withClient(client) }
    }

    fun onDismissClientPicker() = form.update { it.copy(showClientPicker = false) }

    fun onDateChange(date: LocalDate) = editDraft { it.copy(date = date) }

    fun onOpenDatePicker() = form.update { it.copy(showDatePicker = true) }

    fun onDatePicked(date: LocalDate?) {
        form.update { it.copy(showDatePicker = false) }
        if (date != null) onDateChange(date)
    }

    fun onTimeChange(time: LocalTime) = editDraft { it.copy(time = time) }

    fun onOpenTimePicker() = form.update { it.copy(showTimePicker = true) }

    fun onTimePicked(time: LocalTime?) {
        form.update { it.copy(showTimePicker = false) }
        if (time != null) onTimeChange(time)
    }

    fun onAddressChange(addressId: Long?) = editDraft { it.copy(addressId = addressId) }

    fun onPhoneChange(phoneId: Long?) = editDraft { it.copy(phoneId = phoneId) }

    fun onMasterChange(masterId: Long?) = editDraft { it.copy(masterId = masterId) }

    fun onDescriptionChange(text: String) = editDraft { it.copy(description = text) }

    fun onSave() {
        val current = form.value
        if (current.isSaving || current.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = ordersRepository.saveOrder(current.draft)) {
                is SaveOrderResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(OrderEditEvent.Saved(result.orderId, wasNew = current.draft.isNew))
                }
                is SaveOrderResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
            }
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _events.send(OrderEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(showDiscardDialog = false, hasChanges = false) }
        viewModelScope.launch { _events.send(OrderEditEvent.Close) }
    }

    fun onDiscardDismissed() = form.update { it.copy(showDiscardDialog = false) }

    private fun editDraft(change: (OrderDraft) -> OrderDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
        )
    }
}
