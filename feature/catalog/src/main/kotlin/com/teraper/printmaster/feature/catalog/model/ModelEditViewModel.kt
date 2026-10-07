package com.teraper.printmaster.feature.catalog.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.DeleteModelResult
import com.teraper.printmaster.core.data.repository.SaveModelResult
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.feature.catalog.navigation.MODEL_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelForm(
    val isLoading: Boolean = false,
    /** Always ends with an empty cartridge row to type into. */
    val draft: PrinterModelDraft = PrinterModelDraft(cartridges = listOf(CartridgeDraft())),
    val errors: Set<PrinterDraftError> = emptySet(),
    val nameTaken: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: ModelEditDialog? = null,
)

data class ModelEditUiState(
    val form: ModelForm = ModelForm(),
    /** Clients that own this model. */
    val owners: List<ModelOwner> = emptyList(),
    val brands: List<String> = emptyList(),
) {
    val isNew: Boolean get() = form.draft.isNew
}

enum class ModelEditDialog { DISCARD, CONFIRM_DELETE, IN_USE }

sealed interface ModelEditEvent {
    data object Close : ModelEditEvent
}

@HiltViewModel
class ModelEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val modelId: Long = savedStateHandle[MODEL_ID_ARG] ?: 0L
    private var initialDraft = ModelForm().draft
    private var triedToSave = false

    private val form = MutableStateFlow(ModelForm(isLoading = modelId != 0L))

    private val _events = Channel<ModelEditEvent>(Channel.BUFFERED)
    val events: Flow<ModelEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ModelEditUiState> = combine(
        form,
        if (modelId != 0L) catalogRepository.observeModelOwners(modelId) else flowOf(emptyList()),
        catalogRepository.observeCatalog().map { catalog -> catalog.map { it.model.brand }.distinctBy(CatalogNames::key) },
    ) { form, owners, brands ->
        ModelEditUiState(form, owners, brands)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelEditUiState(form.value))

    init {
        if (modelId != 0L) {
            viewModelScope.launch {
                val model = catalogRepository.observeModel(modelId).first()
                if (model == null) {
                    _events.send(ModelEditEvent.Close)
                } else {
                    val draft = PrinterModelDraft.from(model)
                    initialDraft = draft.copy(cartridges = draft.cartridges + CartridgeDraft())
                    form.update { it.copy(isLoading = false, draft = initialDraft) }
                }
            }
        }
    }

    fun onBrandChange(brand: String) = editDraft { it.copy(brand = brand) }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    fun onPrintTypeChange(type: PrintType) = editDraft { it.copy(printType = type) }

    fun onColorTypeChange(type: ColorType) = editDraft { it.copy(colorType = type) }

    fun onCartridgeChange(index: Int, cartridge: CartridgeDraft) =
        editDraft { draft -> draft.copy(cartridges = draft.cartridges.mapIndexed { i, row -> if (i == index) cartridge else row }) }

    fun onRemoveCartridge(index: Int) =
        editDraft { draft -> draft.copy(cartridges = draft.cartridges.filterIndexed { i, _ -> i != index }) }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = catalogRepository.saveModel(state.draft)) {
                is SaveModelResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(ModelEditEvent.Close)
                }
                is SaveModelResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
                SaveModelResult.NameTaken -> form.update { it.copy(isSaving = false, errors = emptySet(), nameTaken = true) }
            }
        }
    }

    fun onDeleteClick() = form.update {
        it.copy(dialog = if (uiState.value.owners.isEmpty()) ModelEditDialog.CONFIRM_DELETE else ModelEditDialog.IN_USE)
    }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (catalogRepository.deleteModel(modelId)) {
                DeleteModelResult.DELETED, DeleteModelResult.NOT_FOUND -> {
                    form.update { it.copy(hasChanges = false) }
                    _events.send(ModelEditEvent.Close)
                }
                DeleteModelResult.IN_USE -> form.update { it.copy(dialog = ModelEditDialog.IN_USE) }
            }
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = ModelEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(ModelEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(ModelEditEvent.Close) }
    }

    fun onDismissDialog() = form.update { it.copy(dialog = null) }

    private fun editDraft(change: (PrinterModelDraft) -> PrinterModelDraft) = form.update { state ->
        var draft = change(state.draft)
        // Keep exactly one empty row at the end to type the next cartridge into.
        val filled = draft.cartridges.dropLastWhile { it.name.isBlank() && it.chips.isBlank() }
        draft = draft.copy(cartridges = filled + CartridgeDraft())
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
            nameTaken = state.nameTaken && draft.brand == state.draft.brand && draft.name == state.draft.name,
        )
    }
}
