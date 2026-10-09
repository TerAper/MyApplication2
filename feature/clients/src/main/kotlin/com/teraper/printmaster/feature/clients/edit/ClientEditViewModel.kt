package com.teraper.printmaster.feature.clients.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.maps.PlaceInbox
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.ClientDraftError
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClientEditUiState(
    val isLoading: Boolean = false,
    val draft: ClientDraft = ClientDraft(),
    /** Errors are only shown after the first Save tap, not while typing. */
    val errors: Set<ClientDraftError> = emptySet(),
    val taxIdTakenBy: String? = null,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val showDiscardDialog: Boolean = false,
) {
    val isNew: Boolean get() = draft.isNew
}

sealed interface ClientEditEvent {
    data class Saved(val clientId: Long, val wasNew: Boolean) : ClientEditEvent
    data object Close : ClientEditEvent

    /** Open a map app to pick the address; [query] is what's typed so far. */
    data class OpenMap(val query: String) : ClientEditEvent
}

/** Which list a contact row belongs to. */
enum class ContactList { PHONES, ADDRESSES }

@HiltViewModel
class ClientEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clientsRepository: ClientsRepository,
    private val placeInbox: PlaceInbox,
) : ViewModel() {

    private val clientId: Long = savedStateHandle[CLIENT_ID_ARG] ?: 0L
    private var initialDraft = ClientDraft()
    private var triedToSave = false

    /** Address row waiting for a place shared back from the map app. */
    private var mapRow: Int? = null

    private val _uiState = MutableStateFlow(ClientEditUiState(isLoading = clientId != 0L))
    val uiState: StateFlow<ClientEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<ClientEditEvent>(Channel.BUFFERED)
    val events: Flow<ClientEditEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            placeInbox.place.filterNotNull().collect {
                val row = mapRow ?: return@collect
                val place = placeInbox.take() ?: return@collect
                mapRow = null
                editContacts(ContactList.ADDRESSES) { rows ->
                    rows.mapIndexed { i, r ->
                        if (i == row) r.copy(value = place.address.ifEmpty { r.value }, mapLink = place.mapLink) else r
                    }
                }
            }
        }
        if (clientId != 0L) {
            viewModelScope.launch {
                val summary = clientsRepository.observeClientSummary(clientId).first()
                if (summary == null) {
                    _events.send(ClientEditEvent.Close)
                } else {
                    initialDraft = ClientDraft.from(summary.client)
                    _uiState.update { it.copy(isLoading = false, draft = initialDraft) }
                }
            }
        }
    }

    fun onTypeChange(type: ClientType) = editDraft { it.copy(type = type) }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    /** Only digits and spaces; ՀՎՀՀ is 8 digits, so a few spaces are plenty. */
    fun onTaxIdChange(value: String) =
        editDraft { it.copy(taxId = value.filter { c -> c.isDigit() || c == ' ' }.take(12)) }

    fun onNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onContactValueChange(list: ContactList, index: Int, value: String) =
        editContacts(list) { rows -> rows.mapIndexed { i, row -> if (i == index) row.copy(value = value) else row } }

    fun onContactLabelChange(list: ContactList, index: Int, label: String) =
        editContacts(list) { rows -> rows.mapIndexed { i, row -> if (i == index) row.copy(label = label) else row } }

    fun onAddContact(list: ContactList) = editContacts(list) { it + ContactDraft() }

    fun onRemoveContact(list: ContactList, index: Int) =
        editContacts(list) { rows -> rows.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(ContactDraft()) } }

    /**
     * A number picked from the phone book goes into phone row [index] (null = a new row, or the
     * first empty one). The contact's name becomes the client's name if none was typed yet,
     * otherwise the phone's label (e.g. "Armen accountant").
     */
    fun onContactPicked(index: Int?, contactName: String?, number: String) {
        val name = contactName?.trim().orEmpty()
        editDraft { draft ->
            val takeName = draft.name.isBlank() && name.isNotEmpty()
            val picked = ContactDraft(value = number.trim(), label = if (takeName) "" else name)
            val target = index ?: draft.phones.indexOfFirst { it.value.isBlank() }.takeIf { it >= 0 }
            val phones = if (target == null) {
                draft.phones + picked
            } else {
                draft.phones.mapIndexed { i, row -> if (i == target) row.copy(value = picked.value, label = row.label.ifBlank { picked.label }) else row }
            }
            draft.copy(name = if (takeName) name else draft.name, phones = phones)
        }
    }

    /** Opens the map for address row [index]; the place comes back when the user shares it to the app. */
    fun onPickOnMap(index: Int) {
        mapRow = index
        placeInbox.startPicking()
        val query = _uiState.value.draft.addresses.getOrNull(index)?.value.orEmpty()
        viewModelScope.launch { _events.send(ClientEditEvent.OpenMap(query)) }
    }

    fun onClearMapPoint(index: Int) =
        editContacts(ContactList.ADDRESSES) { rows -> rows.mapIndexed { i, r -> if (i == index) r.copy(mapLink = null) else r } }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = clientsRepository.saveClient(state.draft)) {
                is SaveClientResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(ClientEditEvent.Saved(result.clientId, wasNew = state.isNew))
                }
                is SaveClientResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.errors) }
                is SaveClientResult.TaxIdTaken -> _uiState.update {
                    it.copy(isSaving = false, errors = emptySet(), taxIdTakenBy = result.otherClientName)
                }
            }
        }
    }

    /** Back / close: ask first if something was typed. */
    fun onCloseRequest() {
        if (_uiState.value.hasChanges) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _events.send(ClientEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        _uiState.update { it.copy(showDiscardDialog = false, hasChanges = false) }
        viewModelScope.launch { _events.send(ClientEditEvent.Close) }
    }

    fun onDiscardDismissed() = _uiState.update { it.copy(showDiscardDialog = false) }

    private fun editContacts(list: ContactList, change: (List<ContactDraft>) -> List<ContactDraft>) = editDraft {
        when (list) {
            ContactList.PHONES -> it.copy(phones = change(it.phones))
            ContactList.ADDRESSES -> it.copy(addresses = change(it.addresses))
        }
    }

    private fun editDraft(change: (ClientDraft) -> ClientDraft) = _uiState.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            // After a failed save, errors update live as the user fixes them.
            errors = if (triedToSave) draft.validate() else emptySet(),
            taxIdTakenBy = state.taxIdTakenBy.takeIf { draft.taxId == state.draft.taxId },
        )
    }
}
