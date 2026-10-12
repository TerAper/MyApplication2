package com.teraper.printmaster.feature.clients.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.ClientItemsRepository
import com.teraper.printmaster.core.data.repository.SaveClientItemResult
import com.teraper.printmaster.core.designsystem.component.usedValues
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypes
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.core.model.SerialNumbers
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ID_ARG
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ITEM_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the model part of the form looks. */
enum class ModelStep {
    /** Nothing chosen yet: search the catalog. */
    SEARCH,

    /** A catalog item is chosen. */
    PICKED,

    /** Typing one that isn't in the catalog. */
    NEW,
}

data class ClientItemForm(
    val isLoading: Boolean = false,
    val draft: ClientItemDraft = ClientItemDraft(),
    val step: ModelStep = ModelStep.SEARCH,
    val modelQuery: String = "",
    /** A part being typed, and which part type it is. */
    val newPart: String = "",
    val newPartTypeId: Long? = null,
    /** Only filled after the first Save tap. */
    val errors: Set<ItemDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: ClientItemEditDialog? = null,
    /** Another item already has this serial: "Gamma · HP LaserJet M125". */
    val serialTakenBy: String? = null,
    /** The phone can't scan (no Google Play services). */
    val scanUnavailable: Boolean = false,
)

data class ClientItemEditUiState(
    val form: ClientItemForm = ClientItemForm(),
    val types: List<ItemType> = emptyList(),
    /** The chosen or typed model's type. */
    val type: ItemType? = null,
    /** Types of parts that go into it. */
    val partTypes: List<ItemType> = emptyList(),
    /** Catalog items matching the search (most owned first when the search is empty). */
    val suggestions: List<CatalogItem> = emptyList(),
    /** Values used before in each text field of the type. */
    val valueSuggestions: Map<Long, List<String>> = emptyMap(),
) {
    val isNew: Boolean get() = form.draft.isNew
    val title: String get() = form.draft.item.title(type)
}

enum class ClientItemEditDialog { DISCARD, CONFIRM_DELETE }

sealed interface ClientItemEditEvent {
    data object Close : ClientItemEditEvent
}

@HiltViewModel
class ClientItemEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    private val clientItemsRepository: ClientItemsRepository,
) : ViewModel() {

    private val clientId: Long = checkNotNull(savedStateHandle[CLIENT_ID_ARG])
    private val clientItemId: Long = savedStateHandle[CLIENT_ITEM_ID_ARG] ?: 0L
    private var initialDraft = ClientItemDraft(clientId = clientId)
    private var triedToSave = false

    private val form = MutableStateFlow(ClientItemForm(isLoading = clientItemId != 0L, draft = initialDraft))

    private val _events = Channel<ClientItemEditEvent>(Channel.BUFFERED)
    val events: Flow<ClientItemEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ClientItemEditUiState> = combine(
        form,
        catalogRepository.observeTypes(),
        catalogRepository.observeCatalog(),
    ) { form, types, catalog ->
        val type = types.firstOrNull { it.id == form.draft.item.typeId }
        ClientItemEditUiState(
            form = form,
            types = types,
            type = type,
            partTypes = type?.let { ItemTypes.partTypes(types, it.id) }.orEmpty(),
            suggestions = if (form.step == ModelStep.SEARCH) suggest(catalog, form.modelQuery) else emptyList(),
            valueSuggestions = type?.let { t -> usedValues(catalog.map { it.item }.filter { it.typeId == t.id }, t.fields) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientItemEditUiState(form.value))

    init {
        if (clientItemId != 0L) {
            viewModelScope.launch {
                val owned = clientItemsRepository.getClientItem(clientItemId)
                if (owned == null) {
                    _events.send(ClientItemEditEvent.Close)
                } else {
                    initialDraft = ClientItemDraft.from(owned)
                    form.update { it.copy(isLoading = false, draft = initialDraft, step = ModelStep.PICKED) }
                }
            }
        }
    }

    fun onModelQueryChange(query: String) = form.update { it.copy(modelQuery = query) }

    fun onModelPicked(model: CatalogItem) {
        form.update { it.copy(step = ModelStep.PICKED) }
        editDraft { it.withItem(model) }
    }

    /** "New «query»": the typed text becomes the name; the type is the only one, or chosen next. */
    fun onStartNewModel() {
        val query = CatalogNames.clean(form.value.modelQuery)
        val types = uiState.value.types
        val typeId = types.singleOrNull()?.id ?: 0L
        form.update { it.copy(step = ModelStep.NEW) }
        editDraft { it.copy(item = it.item.copy(id = 0, typeId = typeId, name = query, values = emptyMap(), parts = emptyList()), selectedParts = emptySet()) }
    }

    fun onNewModelType(typeId: Long) =
        editDraft { it.copy(item = it.item.copy(typeId = typeId, values = emptyMap(), parts = emptyList()), selectedParts = emptySet()) }

    /** Back to searching the catalog. */
    fun onChangeModel() {
        form.update { it.copy(step = ModelStep.SEARCH, modelQuery = "") }
        editDraft { it.copy(item = it.item.copy(id = 0, typeId = 0, name = "", values = emptyMap(), parts = emptyList()), selectedParts = emptySet()) }
    }

    fun onModelNameChange(name: String) = editDraft { it.copy(item = it.item.copy(name = name)) }

    fun onModelValueChange(fieldId: Long, value: String) = editDraft { it.copy(item = it.item.withValue(fieldId, value)) }

    fun onPartToggle(key: String) = editDraft { it.togglePart(key) }

    fun onNewPartChange(name: String) = form.update { it.copy(newPart = name) }

    fun onNewPartType(typeId: Long) = form.update { it.copy(newPartTypeId = typeId) }

    fun onAddPart() {
        val state = form.value
        val typeId = state.newPartTypeId?.takeIf { id -> uiState.value.partTypes.any { it.id == id } } ?: uiState.value.partTypes.firstOrNull()?.id
        if (state.newPart.isBlank() || typeId == null) return
        editDraft { it.addPart(LinkDraft(typeId = typeId, title = state.newPart)) }
        form.update { it.copy(newPart = "") }
    }

    fun onPlaceChange(place: String) = editDraft { it.copy(place = place) }

    fun onNoteChange(note: String) = editDraft { it.copy(note = note) }

    private var serialCheck: Job? = null

    fun onSerialChange(serial: String) {
        editDraft { it.copy(serialNumber = serial) }
        // Is it on another item already (one that moved, or a typo)? Checked once typing pauses.
        serialCheck?.cancel()
        serialCheck = viewModelScope.launch {
            delay(SERIAL_CHECK_DELAY_MS)
            val other = clientItemsRepository.findBySerial(serial).firstOrNull { it.exact && it.item.id != clientItemId }
            form.update { it.copy(serialTakenBy = other?.let { m -> "${m.clientName} · ${m.item.item.title}" }) }
        }
    }

    /** The label's barcode, without "S/N:" and the like. */
    fun onSerialScanned(text: String) {
        form.update { it.copy(scanUnavailable = false) }
        onSerialChange(SerialNumbers.clean(text))
    }

    fun onScanUnavailable() = form.update { it.copy(scanUnavailable = true) }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        // A part typed but not added yet is almost certainly meant to be added.
        if (state.newPart.isNotBlank()) onAddPart()
        val draft = form.value.draft
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = clientItemsRepository.saveClientItem(draft)) {
                is SaveClientItemResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(ClientItemEditEvent.Close)
                }
                is SaveClientItemResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
            }
        }
    }

    fun onDeleteClick() = form.update { it.copy(dialog = ClientItemEditDialog.CONFIRM_DELETE) }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch {
            clientItemsRepository.deleteClientItem(clientItemId)
            _events.send(ClientItemEditEvent.Close)
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = ClientItemEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(ClientItemEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(ClientItemEditEvent.Close) }
    }

    fun onDismissDialog() = form.update { it.copy(dialog = null) }

    private fun editDraft(change: (ClientItemDraft) -> ClientItemDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
        )
    }

    private fun suggest(catalog: List<CatalogEntry>, query: String): List<CatalogItem> {
        val matching = if (query.isBlank()) catalog.sortedByDescending { it.ownedCount } else catalog.filter { CatalogSearch.matches(it.item, query) }
        return matching.take(MAX_SUGGESTIONS).map { it.item }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
        const val SERIAL_CHECK_DELAY_MS = 400L
    }
}
