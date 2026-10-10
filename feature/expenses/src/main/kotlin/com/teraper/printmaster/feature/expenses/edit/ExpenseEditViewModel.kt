package com.teraper.printmaster.feature.expenses.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ExpensesRepository
import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.data.repository.SaveExpenseResult
import com.teraper.printmaster.core.model.ExpenseCategory
import com.teraper.printmaster.core.model.ExpenseDraft
import com.teraper.printmaster.core.model.ExpenseError
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import com.teraper.printmaster.feature.expenses.navigation.EXPENSE_ID_ARG
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
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ExpenseForm(
    val isLoading: Boolean = true,
    val draft: ExpenseDraft,
    val errors: Set<ExpenseError> = emptySet(),
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
)

data class ExpenseEditUiState(val form: ExpenseForm, val today: LocalDate, val photos: List<Photo> = emptyList())

sealed interface ExpenseEditEvent {
    data object Close : ExpenseEditEvent
}

@HiltViewModel
class ExpenseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpensesRepository,
    private val photos: PhotoRepository,
    clock: Clock,
) : ViewModel() {

    private val id: Long = savedStateHandle[EXPENSE_ID_ARG] ?: 0L
    private val today = LocalDate.now(clock)
    private var triedToSave = false
    private val form = MutableStateFlow(ExpenseForm(isLoading = id != 0L, draft = ExpenseDraft(date = today)))

    private val _events = Channel<ExpenseEditEvent>(Channel.BUFFERED)
    val events: Flow<ExpenseEditEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ExpenseEditUiState> = combine(form, photos.observePhotos(PhotoOwner.EXPENSE, listOfNotNull(id.takeIf { it != 0L }))) { form, photos ->
        ExpenseEditUiState(form, today, photos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseEditUiState(form.value, today))

    init {
        if (id != 0L) {
            viewModelScope.launch {
                val expense = repository.observeExpense(id).first()
                if (expense == null) _events.send(ExpenseEditEvent.Close) else form.update { it.copy(isLoading = false, draft = ExpenseDraft.from(expense)) }
            }
        }
    }

    fun onAmountChange(text: String) = edit { it.withAmount(text) }

    fun onCategoryChange(category: ExpenseCategory) = edit { it.copy(category = category) }

    fun onDateChange(date: LocalDate) = edit { it.copy(date = date) }

    fun onNoteChange(note: String) = edit { it.copy(note = note) }

    fun onSave() {
        val state = form.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            when (val result = repository.save(state.draft)) {
                is SaveExpenseResult.Saved -> _events.send(ExpenseEditEvent.Close)
                is SaveExpenseResult.Invalid -> form.update { it.copy(isSaving = false, errors = result.errors) }
            }
        }
    }

    fun onDeleteClick() = form.update { it.copy(confirmDelete = true) }

    fun onDismissDelete() = form.update { it.copy(confirmDelete = false) }

    fun onConfirmDelete() {
        form.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            repository.delete(id)
            _events.send(ExpenseEditEvent.Close)
        }
    }

    /** Receipt photos: only on a saved expense. */
    fun onAddPhoto(uri: String) {
        if (id == 0L) return
        viewModelScope.launch { photos.add(PhotoOwner.EXPENSE, id, uri) }
    }

    fun onDeletePhoto(photo: Photo) {
        viewModelScope.launch { photos.delete(photo.id) }
    }

    val isNew: Boolean get() = id == 0L

    private fun edit(change: (ExpenseDraft) -> ExpenseDraft) = form.update { state ->
        val draft = change(state.draft)
        state.copy(draft = draft, errors = if (triedToSave) draft.validate() else emptySet())
    }
}
