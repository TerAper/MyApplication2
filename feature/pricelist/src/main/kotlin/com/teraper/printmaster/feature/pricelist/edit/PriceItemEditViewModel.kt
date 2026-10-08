package com.teraper.printmaster.feature.pricelist.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.PriceListRepository
import com.teraper.printmaster.core.data.repository.SavePriceItemResult
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.feature.pricelist.navigation.CATEGORY_ARG
import com.teraper.printmaster.feature.pricelist.navigation.ITEM_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceItemEditUiState(
    val isLoading: Boolean = false,
    val draft: PriceItemDraft = PriceItemDraft(),
    val errors: Set<PriceItemError> = emptySet(),
    val nameTaken: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: PriceItemEditDialog? = null,
) {
    val isNew: Boolean get() = draft.isNew
}

enum class PriceItemEditDialog { DISCARD, CONFIRM_DELETE }

sealed interface PriceItemEditEvent {
    data object Close : PriceItemEditEvent
}

@HiltViewModel
class PriceItemEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val priceListRepository: PriceListRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle[ITEM_ID_ARG] ?: 0L
    private var initialDraft = PriceItemDraft(
        category = savedStateHandle.get<String>(CATEGORY_ARG)
            ?.let { name -> RepairCategory.entries.firstOrNull { it.name == name } }
            ?: RepairCategory.CARTRIDGE,
    )
    private var triedToSave = false

    private val _uiState = MutableStateFlow(PriceItemEditUiState(isLoading = itemId != 0L, draft = initialDraft))
    val uiState: StateFlow<PriceItemEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<PriceItemEditEvent>(Channel.BUFFERED)
    val events: Flow<PriceItemEditEvent> = _events.receiveAsFlow()

    init {
        if (itemId != 0L) {
            viewModelScope.launch {
                val item = priceListRepository.observeItem(itemId).first()
                if (item == null) {
                    _events.send(PriceItemEditEvent.Close)
                } else {
                    initialDraft = PriceItemDraft.from(item)
                    _uiState.update { it.copy(isLoading = false, draft = initialDraft) }
                }
            }
        }
    }

    fun onCategoryChange(category: RepairCategory) = editDraft { it.copy(category = category) }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    fun onDescriptionChange(description: String) = editDraft { it.copy(description = description) }

    fun onPriceChange(text: String) = editDraft { it.withPrice(text) }

    fun onCostChange(text: String) = editDraft { it.withCost(text) }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = priceListRepository.saveItem(state.draft)) {
                is SavePriceItemResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(PriceItemEditEvent.Close)
                }
                is SavePriceItemResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.errors) }
                SavePriceItemResult.NameTaken -> _uiState.update { it.copy(isSaving = false, errors = emptySet(), nameTaken = true) }
            }
        }
    }

    fun onDeleteClick() = _uiState.update { it.copy(dialog = PriceItemEditDialog.CONFIRM_DELETE) }

    fun onConfirmDelete() {
        _uiState.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch {
            priceListRepository.deleteItem(itemId)
            _events.send(PriceItemEditEvent.Close)
        }
    }

    fun onCloseRequest() {
        if (_uiState.value.hasChanges) {
            _uiState.update { it.copy(dialog = PriceItemEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(PriceItemEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        _uiState.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(PriceItemEditEvent.Close) }
    }

    fun onDismissDialog() = _uiState.update { it.copy(dialog = null) }

    private fun editDraft(change: (PriceItemDraft) -> PriceItemDraft) = _uiState.update { state ->
        val draft = change(state.draft)
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
            nameTaken = state.nameTaken && draft.name == state.draft.name && draft.category == state.draft.category,
        )
    }
}
