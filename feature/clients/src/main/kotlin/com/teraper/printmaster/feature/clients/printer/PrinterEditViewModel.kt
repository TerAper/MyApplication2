package com.teraper.printmaster.feature.clients.printer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.PrintersRepository
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.CatalogSearch
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.feature.clients.navigation.CLIENT_ID_ARG
import com.teraper.printmaster.feature.clients.navigation.PRINTER_ID_ARG
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
import javax.inject.Inject

/** How the model part of the form looks. */
enum class ModelStep {
    /** Nothing chosen yet: search the catalog. */
    SEARCH,

    /** A catalog model is chosen. */
    PICKED,

    /** Typing a model that isn't in the catalog. */
    NEW,
}

data class PrinterForm(
    val isLoading: Boolean = false,
    val draft: ClientPrinterDraft = ClientPrinterDraft(),
    val step: ModelStep = ModelStep.SEARCH,
    val modelQuery: String = "",
    val newCartridge: CartridgeDraft = CartridgeDraft(),
    /** Only filled after the first Save tap. */
    val errors: Set<PrinterDraftError> = emptySet(),
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: PrinterEditDialog? = null,
)

data class PrinterEditUiState(
    val form: PrinterForm = PrinterForm(),
    /** Catalog models matching the search (most used first when the search is empty). */
    val suggestions: List<PrinterModel> = emptyList(),
    /** Brands already in the catalog, as one-tap choices for a new model. */
    val brands: List<String> = emptyList(),
) {
    val isNew: Boolean get() = form.draft.isNew
}

enum class PrinterEditDialog { DISCARD, CONFIRM_DELETE }

sealed interface PrinterEditEvent {
    data object Close : PrinterEditEvent
}

@HiltViewModel
class PrinterEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalogRepository: CatalogRepository,
    private val printersRepository: PrintersRepository,
) : ViewModel() {

    private val clientId: Long = checkNotNull(savedStateHandle[CLIENT_ID_ARG])
    private val printerId: Long = savedStateHandle[PRINTER_ID_ARG] ?: 0L
    private var initialDraft = ClientPrinterDraft(clientId = clientId)
    private var triedToSave = false

    private val form = MutableStateFlow(PrinterForm(isLoading = printerId != 0L, draft = initialDraft))

    private val _events = Channel<PrinterEditEvent>(Channel.BUFFERED)
    val events: Flow<PrinterEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<PrinterEditUiState> = combine(form, catalogRepository.observeCatalog()) { form, catalog ->
        PrinterEditUiState(
            form = form,
            suggestions = if (form.step == ModelStep.SEARCH) suggest(catalog, form.modelQuery) else emptyList(),
            brands = catalog.map { it.model.brand }.distinctBy(CatalogNames::key),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrinterEditUiState(form.value))

    init {
        if (printerId != 0L) {
            viewModelScope.launch {
                val printer = printersRepository.getPrinter(printerId)
                if (printer == null) {
                    _events.send(PrinterEditEvent.Close)
                } else {
                    initialDraft = ClientPrinterDraft.from(printer)
                    form.update { it.copy(isLoading = false, draft = initialDraft, step = ModelStep.PICKED) }
                }
            }
        }
    }

    fun onModelQueryChange(query: String) = form.update { it.copy(modelQuery = query) }

    fun onModelPicked(model: PrinterModel) {
        form.update { it.copy(step = ModelStep.PICKED) }
        editDraft { it.withModel(model) }
    }

    /** "New model «query»": guess the brand from the first word when it's a known brand. */
    fun onStartNewModel() {
        val query = CatalogNames.clean(form.value.modelQuery)
        val knownBrand = uiState.value.brands.firstOrNull { brand ->
            query.length > brand.length && CatalogNames.key(query).startsWith(CatalogNames.key(brand) + " ")
        }
        val (brand, name) = when {
            knownBrand != null -> knownBrand to query.substring(knownBrand.length).trim()
            ' ' in query -> query.substringBefore(' ') to query.substringAfter(' ')
            else -> "" to query
        }
        form.update { it.copy(step = ModelStep.NEW) }
        editDraft { it.copy(model = it.model.copy(id = 0, brand = brand, name = name, cartridges = emptyList()), selectedCartridges = emptySet()) }
    }

    /** Back to searching the catalog. */
    fun onChangeModel() {
        form.update { it.copy(step = ModelStep.SEARCH, modelQuery = "") }
        editDraft { it.copy(model = it.model.copy(id = 0, brand = "", name = "", cartridges = emptyList()), selectedCartridges = emptySet()) }
    }

    fun onBrandChange(brand: String) = editDraft { it.copy(model = it.model.copy(brand = brand)) }

    fun onModelNameChange(name: String) = editDraft { it.copy(model = it.model.copy(name = name)) }

    fun onPrintTypeChange(type: PrintType) = editDraft { it.copy(model = it.model.copy(printType = type)) }

    fun onColorTypeChange(type: ColorType) = editDraft { it.copy(model = it.model.copy(colorType = type)) }

    fun onCartridgeToggle(key: String) = editDraft { it.toggleCartridge(key) }

    fun onNewCartridgeChange(cartridge: CartridgeDraft) = form.update { it.copy(newCartridge = cartridge) }

    fun onAddCartridge() {
        val cartridge = form.value.newCartridge
        if (cartridge.name.isBlank()) return
        editDraft { it.addCartridge(cartridge) }
        form.update { it.copy(newCartridge = CartridgeDraft()) }
    }

    fun onLocationChange(location: String) = editDraft { it.copy(location = location) }

    fun onNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        // A cartridge typed but not added yet is almost certainly meant to be added.
        if (state.newCartridge.name.isNotBlank()) onAddCartridge()
        val draft = form.value.draft
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = printersRepository.savePrinter(draft)) {
                is SavePrinterResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(PrinterEditEvent.Close)
                }
                is SavePrinterResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
            }
        }
    }

    fun onDeleteClick() = form.update { it.copy(dialog = PrinterEditDialog.CONFIRM_DELETE) }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch {
            printersRepository.deletePrinter(printerId)
            _events.send(PrinterEditEvent.Close)
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = PrinterEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(PrinterEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(PrinterEditEvent.Close) }
    }

    fun onDismissDialog() = form.update { it.copy(dialog = null) }

    private fun editDraft(change: (ClientPrinterDraft) -> ClientPrinterDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
        )
    }

    private fun suggest(catalog: List<CatalogModel>, query: String): List<PrinterModel> {
        val matching = if (query.isBlank()) catalog.sortedByDescending { it.printerCount } else catalog.filter { CatalogSearch.matches(it.model, query) }
        return matching.take(MAX_SUGGESTIONS).map { it.model }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 6
    }
}
