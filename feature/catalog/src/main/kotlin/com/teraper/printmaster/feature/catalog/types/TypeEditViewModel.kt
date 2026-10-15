package com.teraper.printmaster.feature.catalog.types

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.DeleteResult
import com.teraper.printmaster.core.data.repository.SaveTypeResult
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.ItemTypeError
import com.teraper.printmaster.core.model.ItemTypes
import com.teraper.printmaster.feature.catalog.navigation.TYPE_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TypeForm(
    val isLoading: Boolean = false,
    val draft: ItemTypeDraft = ItemTypeDraft(fields = listOf(FieldDraft())),
    /** Only filled after the first Save tap. */
    val errors: Set<ItemTypeError> = emptySet(),
    val nameTaken: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: TypeEditDialog? = null,
)

data class TypeEditUiState(
    val form: TypeForm = TypeForm(),
    /** Types this one can go into: not itself nor one of its own parts. */
    val parents: List<ItemType> = emptyList(),
) {
    val isNew: Boolean get() = form.draft.isNew
}

enum class TypeEditDialog { DISCARD, CONFIRM_DELETE, IN_USE }

sealed interface TypeEditEvent {
    data object Close : TypeEditEvent
}

@HiltViewModel
class TypeEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val typeId: Long = savedStateHandle[TYPE_ID_ARG] ?: 0L
    private var initialDraft = TypeForm().draft
    private var triedToSave = false

    private val form = MutableStateFlow(TypeForm(isLoading = typeId != 0L))

    private val _events = Channel<TypeEditEvent>(Channel.BUFFERED)
    val events: Flow<TypeEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<TypeEditUiState> = combine(form, catalogRepository.observeTypes()) { form, types ->
        val below = if (typeId == 0L) emptySet() else ItemTypes.selfAndBelow(types, typeId)
        TypeEditUiState(form, types.filter { it.id !in below })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeEditUiState(form.value))

    init {
        if (typeId != 0L) {
            viewModelScope.launch {
                val type = catalogRepository.observeTypes().first().firstOrNull { it.id == typeId }
                if (type == null) {
                    _events.send(TypeEditEvent.Close)
                } else {
                    val draft = ItemTypeDraft.from(type)
                    initialDraft = draft.copy(fields = draft.fields + FieldDraft())
                    form.update { it.copy(isLoading = false, draft = initialDraft) }
                }
            }
        }
    }

    fun onNameChange(name: String) = editDraft { it.copy(name = name) }

    fun onParentChange(parentId: Long?) = editDraft { it.copy(parentId = parentId) }

    fun onHasSerialChange(on: Boolean) = editDraft { it.copy(hasSerial = on) }

    fun onPerClientChange(on: Boolean) = editDraft { it.copy(perClient = on) }

    fun onHasPhotosChange(on: Boolean) = editDraft { it.copy(hasPhotos = on) }

    fun onFieldChange(index: Int, field: FieldDraft) =
        editDraft { draft -> draft.copy(fields = draft.fields.mapIndexed { i, row -> if (i == index) field else row }) }

    fun onRemoveField(index: Int) = editDraft { draft -> draft.copy(fields = draft.fields.filterIndexed { i, _ -> i != index }) }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = catalogRepository.saveType(state.draft)) {
                is SaveTypeResult.Saved -> {
                    form.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(TypeEditEvent.Close)
                }
                is SaveTypeResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
                SaveTypeResult.NameTaken -> form.update { it.copy(isSaving = false, errors = emptySet(), nameTaken = true) }
            }
        }
    }

    fun onDeleteClick() = form.update { it.copy(dialog = TypeEditDialog.CONFIRM_DELETE) }

    fun onConfirmDelete() {
        form.update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (catalogRepository.deleteType(typeId)) {
                DeleteResult.DELETED, DeleteResult.NOT_FOUND, DeleteResult.HAS_STOCK -> {
                    form.update { it.copy(hasChanges = false) }
                    _events.send(TypeEditEvent.Close)
                }
                DeleteResult.IN_USE -> form.update { it.copy(dialog = TypeEditDialog.IN_USE) }
            }
        }
    }

    fun onCloseRequest() {
        if (form.value.hasChanges) {
            form.update { it.copy(dialog = TypeEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(TypeEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        form.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(TypeEditEvent.Close) }
    }

    fun onDismissDialog() = form.update { it.copy(dialog = null) }

    private fun editDraft(change: (ItemTypeDraft) -> ItemTypeDraft) = form.update { state ->
        var draft = change(state.draft)
        // Keep exactly one empty field at the end to type the next one into.
        val filled = draft.fields.dropLastWhile { it.name.isBlank() && it.options.isBlank() }
        draft = draft.copy(fields = filled + FieldDraft())
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
            nameTaken = state.nameTaken && draft.name == state.draft.name,
        )
    }
}
