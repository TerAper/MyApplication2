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
import com.teraper.printmaster.core.model.ClientPartDraft
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypes
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

/** How the "what it is" part of the form looks. */
enum class ModelStep {
    /** Search the catalog (of the chosen type, if one is). */
    SEARCH,

    /** A catalog item is chosen. */
    PICKED,

    /** Typing a new one: a model not in the catalog yet, or a one-of-a-kind item (a child). */
    NEW,
}

/** The sheet for one part: [index] in the draft's parts, null = adding. */
data class PartEditor(val index: Int?, val draft: ClientPartDraft)

data class ClientItemForm(
    val isLoading: Boolean = false,
    val draft: ClientItemDraft = ClientItemDraft(),
    val step: ModelStep = ModelStep.SEARCH,
    val modelQuery: String = "",
    /** Only filled after the first Save tap. */
    val errors: Set<ItemDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: ClientItemEditDialog? = null,
    val partEditor: PartEditor? = null,
    /** Another item already has this serial: "Gamma · HP LaserJet M125". */
    val serialTakenBy: String? = null,
    /** The phone can't scan (no Google Play services). */
    val scanUnavailable: Boolean = false,
)

data class ClientItemEditUiState(
    val form: ClientItemForm = ClientItemForm(),
    val types: List<ItemType> = emptyList(),
    /** The chosen type. */
    val type: ItemType? = null,
    /** Types of parts that go into it. */
    val partTypes: List<ItemType> = emptyList(),
    /** Catalog items matching the search (most owned first when the search is empty). */
    val suggestions: List<CatalogItem> = emptyList(),
    /** Values used before in each text field, of the type and of its part types. */
    val valueSuggestions: Map<Long, List<String>> = emptyMap(),
    /** For the open part sheet: catalog items of that part type, ones that fit the model first. */
    val partChoices: List<CatalogItem> = emptyList(),
) {
    val isNew: Boolean get() = form.draft.isNew
    val title: String get() = form.draft.item.title(type)
    val partType: ItemType? get() = form.partEditor?.let { e -> partTypes.firstOrNull { it.id == e.draft.typeId } }
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
        val partTypes = type?.let { ItemTypes.partTypes(types, it.id) }.orEmpty()
        val items = catalog.map { it.item }
        ClientItemEditUiState(
            form = form,
            types = types,
            type = type,
            partTypes = partTypes,
            suggestions = if (form.step == ModelStep.SEARCH) suggest(catalog, form.modelQuery, type) else emptyList(),
            valueSuggestions = (listOfNotNull(type) + partTypes)
                .map { t -> usedValues(items.filter { it.typeId == t.id }, t.fields) }
                .fold(emptyMap()) { all, one -> all + one },
            partChoices = form.partEditor?.let { partChoices(items, it, form.draft) }.orEmpty(),
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
                    val step = if (owned.item == null) ModelStep.NEW else ModelStep.PICKED
                    form.update { it.copy(isLoading = false, draft = initialDraft, step = step) }
                }
            }
        }
    }

    /** A one-of-a-kind type (a child) is filled in right away; any other is searched in the catalog. */
    fun onTypePicked(type: ItemType) {
        val current = form.value.draft.item
        if (current.typeId == type.id) return
        form.update { it.copy(step = if (type.perClient) ModelStep.NEW else ModelStep.SEARCH, modelQuery = "") }
        editDraft { it.copy(item = ItemDraft(typeId = type.id, name = if (type.perClient) current.name else ""), parts = emptyList()) }
    }

    fun onModelQueryChange(query: String) = form.update { it.copy(modelQuery = query) }

    fun onModelPicked(model: CatalogItem) {
        form.update { it.copy(step = ModelStep.PICKED) }
        editDraft { it.withItem(model) }
    }

    /** "New «query»": the typed text becomes the name; the type is the chosen one, or the only one. */
    fun onStartNewModel() {
        val query = CatalogNames.clean(form.value.modelQuery)
        val typeId = form.value.draft.item.typeId.takeIf { it != 0L } ?: uiState.value.types.singleOrNull()?.id ?: 0L
        form.update { it.copy(step = ModelStep.NEW) }
        editDraft { it.copy(item = ItemDraft(typeId = typeId, name = query), parts = it.parts.filter { p -> p.isSpec }) }
    }

    /** Back to searching the catalog of the same type. */
    fun onChangeModel() {
        form.update { it.copy(step = ModelStep.SEARCH, modelQuery = "") }
        editDraft { it.copy(item = ItemDraft(typeId = it.item.typeId), parts = emptyList()) }
    }

    fun onModelNameChange(name: String) = editDraft { it.copy(item = it.item.copy(name = name)) }

    fun onModelValueChange(fieldId: Long, value: String) = editDraft { it.copy(item = it.item.withValue(fieldId, value)) }

    // Parts

    fun onAddPart(type: ItemType) = form.update { it.copy(partEditor = PartEditor(null, ClientPartDraft(typeId = type.id))) }

    fun onEditPart(index: Int) = form.update { state ->
        state.draft.parts.getOrNull(index)?.let { state.copy(partEditor = PartEditor(index, it)) } ?: state
    }

    /** A catalog item for the part; tapping the chosen one again goes back to describing it. */
    fun onPartItemPicked(item: CatalogItem) = editPart { part ->
        if (part.itemId == item.id) part.copy(itemId = 0, title = "") else part.copy(itemId = item.id, title = item.title, values = emptyMap())
    }

    /** A name typed: a catalog item by that name (made if missing); empty = describe it by its values. */
    fun onPartNameChange(name: String) = editPart { it.copy(itemId = 0, title = name) }

    fun onPartValueChange(fieldId: Long, value: String) = editPart { it.withValue(fieldId, value) }

    fun onPartSerialChange(serial: String) = editPart { it.copy(serialNumber = serial) }

    fun onSavePart() {
        val editor = form.value.partEditor ?: return
        val part = editor.draft.copy(title = CatalogNames.clean(editor.draft.title))
        editDraft { draft -> if (editor.index == null) draft.addPart(part) else draft.withPart(editor.index, part) }
        form.update { it.copy(partEditor = null) }
    }

    fun onRemovePart() {
        val index = form.value.partEditor?.index
        form.update { it.copy(partEditor = null) }
        if (index != null) editDraft { it.removePart(index) }
    }

    fun onDismissPart() = form.update { it.copy(partEditor = null) }

    // Where it is

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
            form.update { it.copy(serialTakenBy = other?.let { m -> "${m.clientName} · ${m.item.title}" }) }
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
        val draft = state.draft
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

    private fun editPart(change: (ClientPartDraft) -> ClientPartDraft) = form.update { state ->
        state.partEditor?.let { state.copy(partEditor = it.copy(draft = change(it.draft))) } ?: state
    }

    private fun editDraft(change: (ClientItemDraft) -> ClientItemDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
        )
    }

    private fun suggest(catalog: List<CatalogEntry>, query: String, type: ItemType?): List<CatalogItem> {
        val ofType = if (type == null) catalog else catalog.filter { it.item.typeId == type.id }
        val matching = if (query.isBlank()) ofType.sortedByDescending { it.ownedCount } else ofType.filter { CatalogSearch.matches(it.item, query) }
        return matching.take(MAX_SUGGESTIONS).map { it.item }
    }

    /** Items of the part's type; those that fit the chosen model first, then by the name typed. */
    private fun partChoices(items: List<CatalogItem>, editor: PartEditor, draft: ClientItemDraft): List<CatalogItem> {
        val fitting = draft.item.parts.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        val query = editor.draft.title.takeIf { editor.draft.itemId == 0L }.orEmpty()
        return items.filter { it.typeId == editor.draft.typeId && (query.isBlank() || CatalogSearch.matches(it, query)) }
            .sortedByDescending { it.id in fitting }
            .take(MAX_PART_CHOICES)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
        const val MAX_PART_CHOICES = 12
        const val SERIAL_CHECK_DELAY_MS = 400L
    }
}
