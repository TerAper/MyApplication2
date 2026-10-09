package com.teraper.printmaster.feature.imports.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.model.ImportBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val batches: List<ImportBatch> = emptyList(),
    /** Asking before undoing this import. */
    val confirmUndo: ImportBatch? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repository: ImportRepository) : ViewModel() {

    private val confirmUndo = MutableStateFlow<ImportBatch?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(repository.observeBatches(), confirmUndo) { batches, confirm ->
        HistoryUiState(false, batches, confirm)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onUndoClick(batch: ImportBatch) {
        confirmUndo.value = batch
    }

    fun onConfirmUndo() {
        val batch = confirmUndo.value ?: return
        confirmUndo.value = null
        viewModelScope.launch { repository.undo(batch.id) }
    }

    fun onDismissUndo() {
        confirmUndo.value = null
    }
}
