package com.teraper.printmaster.feature.settings.columns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ImportColumnsRepository
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The column being edited: its titles as typed, one per line. */
data class ColumnEdit(val column: ImportColumn, val text: String)

data class ExcelColumnsUiState(
    val isLoading: Boolean = true,
    val columns: ImportColumns = ImportColumns.DEFAULT,
    val editing: ColumnEdit? = null,
    /** "Reset all columns of this file?" */
    val confirmReset: ImportKind? = null,
)

@HiltViewModel
class ExcelColumnsViewModel @Inject constructor(private val repository: ImportColumnsRepository) : ViewModel() {

    private val local = MutableStateFlow(ExcelColumnsUiState())

    val uiState: StateFlow<ExcelColumnsUiState> = combine(repository.observeColumns(), local) { columns, state ->
        state.copy(isLoading = false, columns = columns)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExcelColumnsUiState())

    fun onEdit(column: ImportColumn) =
        local.update { it.copy(editing = ColumnEdit(column, uiState.value.columns.titles(column).joinToString("\n"))) }

    fun onEditTextChange(text: String) = local.update { state -> state.copy(editing = state.editing?.copy(text = text)) }

    fun onSaveEdit() {
        val edit = local.value.editing ?: return
        local.update { it.copy(editing = null) }
        viewModelScope.launch { repository.setTitles(edit.column, edit.text.lines()) }
    }

    /** Puts the default titles in the box; Save keeps them. */
    fun onUseDefault() = local.update { state -> state.copy(editing = state.editing?.let { it.copy(text = it.column.defaults.joinToString("\n")) }) }

    fun onDismissEdit() = local.update { it.copy(editing = null) }

    fun onResetClick(kind: ImportKind) = local.update { it.copy(confirmReset = kind) }

    fun onDismissReset() = local.update { it.copy(confirmReset = null) }

    fun onConfirmReset() {
        val kind = local.value.confirmReset ?: return
        local.update { it.copy(confirmReset = null) }
        viewModelScope.launch { repository.reset(kind) }
    }
}
