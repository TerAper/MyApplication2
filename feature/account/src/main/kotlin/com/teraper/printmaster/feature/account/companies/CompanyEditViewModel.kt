package com.teraper.printmaster.feature.account.companies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.DeleteCompanyResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import com.teraper.printmaster.feature.account.navigation.COMPANY_ID_ARG
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

data class CompanyEditUiState(
    val isLoading: Boolean = false,
    val draft: CompanyDraft = CompanyDraft(),
    val errors: Set<CompanyDraftError> = emptySet(),
    val taxIdTakenBy: String? = null,
    val isDefault: Boolean = false,
    /** A company in COMPANY mode is the only one, so it can't be deleted or made default. */
    val canDelete: Boolean = false,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val dialog: CompanyEditDialog? = null,
) {
    val isNew: Boolean get() = draft.isNew
}

enum class CompanyEditDialog { DISCARD, CONFIRM_DELETE, DELETE_DEFAULT, DELETE_HAS_RECORDS }

sealed interface CompanyEditEvent {
    data object Close : CompanyEditEvent
}

@HiltViewModel
class CompanyEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val companiesRepository: CompaniesRepository,
) : ViewModel() {

    private val companyId: Long = savedStateHandle[COMPANY_ID_ARG] ?: 0L
    private var initialDraft = CompanyDraft(colorIndex = 0)
    private var triedToSave = false

    private val _uiState = MutableStateFlow(CompanyEditUiState(isLoading = true))
    val uiState: StateFlow<CompanyEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<CompanyEditEvent>(Channel.BUFFERED)
    val events: Flow<CompanyEditEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val companies = companiesRepository.observeCompanies().first()
            val profile = companiesRepository.observeProfile().first()
            val company = companies.firstOrNull { it.id == companyId }
            if (companyId != 0L && company == null) {
                _events.send(CompanyEditEvent.Close)
                return@launch
            }
            // A new company gets the first color nobody uses yet.
            initialDraft = company?.let(CompanyDraft::from)
                ?: CompanyDraft(colorIndex = (0 until 8).firstOrNull { c -> companies.none { it.colorIndex == c } } ?: companies.size)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    draft = initialDraft,
                    isDefault = profile?.defaultCompanyId == companyId,
                    canDelete = company != null && companies.size > 1,
                )
            }
        }
    }

    fun onChange(draft: CompanyDraft) = _uiState.update { state ->
        state.copy(
            draft = draft,
            hasChanges = draft != initialDraft,
            errors = if (triedToSave) draft.validate() else emptySet(),
            taxIdTakenBy = state.taxIdTakenBy.takeIf { draft.taxId == state.draft.taxId },
        )
    }

    fun onSave() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading) return
        triedToSave = true
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = companiesRepository.saveCompany(state.draft)) {
                is SaveCompanyResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false, hasChanges = false) }
                    _events.send(CompanyEditEvent.Close)
                }
                is SaveCompanyResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.errors) }
                is SaveCompanyResult.TaxIdTaken -> _uiState.update { it.copy(isSaving = false, taxIdTakenBy = result.otherCompanyName) }
            }
        }
    }

    fun onMakeDefault() {
        viewModelScope.launch {
            companiesRepository.setDefaultCompany(companyId)
            _uiState.update { it.copy(isDefault = true) }
        }
    }

    fun onDeleteClick() = _uiState.update {
        it.copy(dialog = if (it.isDefault) CompanyEditDialog.DELETE_DEFAULT else CompanyEditDialog.CONFIRM_DELETE)
    }

    fun onConfirmDelete() {
        _uiState.update { it.copy(dialog = null) }
        viewModelScope.launch {
            when (companiesRepository.deleteCompany(companyId)) {
                DeleteCompanyResult.DELETED, DeleteCompanyResult.NOT_FOUND -> {
                    _uiState.update { it.copy(hasChanges = false) }
                    _events.send(CompanyEditEvent.Close)
                }
                DeleteCompanyResult.IS_DEFAULT -> _uiState.update { it.copy(dialog = CompanyEditDialog.DELETE_DEFAULT) }
                DeleteCompanyResult.HAS_RECORDS -> _uiState.update { it.copy(dialog = CompanyEditDialog.DELETE_HAS_RECORDS) }
            }
        }
    }

    fun onCloseRequest() {
        if (_uiState.value.hasChanges) {
            _uiState.update { it.copy(dialog = CompanyEditDialog.DISCARD) }
        } else {
            viewModelScope.launch { _events.send(CompanyEditEvent.Close) }
        }
    }

    fun onDiscardConfirmed() {
        _uiState.update { it.copy(dialog = null, hasChanges = false) }
        viewModelScope.launch { _events.send(CompanyEditEvent.Close) }
    }

    fun onDismissDialog() = _uiState.update { it.copy(dialog = null) }
}
