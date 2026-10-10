package com.teraper.printmaster.feature.imports.file

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ImportRepository
import com.teraper.printmaster.core.model.CompanyCheck
import com.teraper.printmaster.core.model.ImportPreview
import com.teraper.printmaster.core.model.ImportPreviewResult
import com.teraper.printmaster.core.model.ImportResult
import com.teraper.printmaster.core.model.MissingColumns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportError { NOT_EXCEL, UNKNOWN_LAYOUT, NO_COMPANY, NOTHING_TO_IMPORT }

sealed interface ImportUiState {
    data object PickFile : ImportUiState
    data object Reading : ImportUiState
    data class Previewing(
        val preview: ImportPreview,
        /** The company had no ՀՎՀՀ/account: save the file's on it. */
        val saveIdentity: Boolean = true,
        /** "This file seems to be another company's — import anyway?" */
        val askDifferentCompany: Boolean = false,
        val importing: Boolean = false,
    ) : ImportUiState {
        val hasSomethingNew: Boolean get() = preview.newRows > 0 || preview.changed > 0
    }
    data class Done(val preview: ImportPreview, val result: ImportResult) : ImportUiState
    /** [missing]: the file looks like one of the two, but lacks these needed columns. */
    data class Failed(val error: ImportError, val missing: MissingColumns? = null) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(private val repository: ImportRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.PickFile)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** [uri] null = the user closed the file picker. */
    fun onFilePicked(uri: String?, fileName: String) {
        if (uri == null) return
        viewModelScope.launch {
            _uiState.value = ImportUiState.Reading
            _uiState.value = when (val result = repository.preview(uri, fileName)) {
                is ImportPreviewResult.Ready -> ImportUiState.Previewing(result.preview)
                ImportPreviewResult.NotExcel -> ImportUiState.Failed(ImportError.NOT_EXCEL)
                is ImportPreviewResult.UnknownLayout -> ImportUiState.Failed(ImportError.UNKNOWN_LAYOUT, result.missing)
                ImportPreviewResult.NoCompany -> ImportUiState.Failed(ImportError.NO_COMPANY)
            }
        }
    }

    fun onSaveIdentityChange(save: Boolean) = updatePreview { it.copy(saveIdentity = save) }

    /** A file that seems to be another company's needs a second "yes". */
    fun onImportClick() {
        val state = _uiState.value as? ImportUiState.Previewing ?: return
        if (state.preview.companyCheck == CompanyCheck.DIFFERENT && !state.askDifferentCompany) {
            updatePreview { it.copy(askDifferentCompany = true) }
        } else {
            runImport()
        }
    }

    fun onConfirmDifferentCompany() = runImport()

    fun onDismissDifferentCompany() = updatePreview { it.copy(askDifferentCompany = false) }

    /** Back to choosing a file. */
    fun onStartOver() {
        _uiState.value = ImportUiState.PickFile
    }

    private fun runImport() {
        val state = _uiState.value as? ImportUiState.Previewing ?: return
        if (state.importing) return
        _uiState.value = state.copy(importing = true, askDifferentCompany = false)
        viewModelScope.launch {
            val result = repository.importPreviewed(saveCompanyIdentity = state.saveIdentity)
            _uiState.value = if (result == null) ImportUiState.Failed(ImportError.NOTHING_TO_IMPORT) else ImportUiState.Done(state.preview, result)
        }
    }

    private fun updatePreview(change: (ImportUiState.Previewing) -> ImportUiState.Previewing) =
        _uiState.update { if (it is ImportUiState.Previewing) change(it) else it }
}
