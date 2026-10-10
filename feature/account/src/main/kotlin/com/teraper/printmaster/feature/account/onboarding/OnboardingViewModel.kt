package com.teraper.printmaster.feature.account.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val ownerName: String = "",
    val company: CompanyDraft = CompanyDraft(),
    val errors: Set<CompanyDraftError> = emptySet(),
    val ownerNameMissing: Boolean = false,
    val isSaving: Boolean = false,
    /** Signed-in Google account, shown so the user knows which one owns the data. */
    val email: String? = null,
)

/**
 * First launch, after Google sign-in: the user's name and first own company. When it's saved
 * the app's start screen notices the new profile by itself, so there's no "done" event here.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val companiesRepository: CompaniesRepository,
    team: TeamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var triedToSave = false

    init {
        // The Google account's name is a good guess; the user can change it.
        viewModelScope.launch {
            val account = team.observeState().first()
            _uiState.update { it.copy(ownerName = it.ownerName.ifEmpty { account.displayName.orEmpty() }, email = account.email) }
        }
    }

    fun onOwnerNameChange(name: String) = _uiState.update {
        it.copy(ownerName = name, ownerNameMissing = triedToSave && name.isBlank())
    }

    fun onCompanyChange(company: CompanyDraft) = _uiState.update {
        it.copy(company = company, errors = if (triedToSave) company.validate() else emptySet())
    }

    fun onRegister() {
        val state = _uiState.value
        if (state.isSaving) return
        triedToSave = true
        val ownerMissing = state.ownerName.isBlank()
        val errors = state.company.validate()
        if (ownerMissing || errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors, ownerNameMissing = ownerMissing) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = companiesRepository.register(AccountMode.OWNER, state.ownerName, state.company)) {
                is SaveCompanyResult.Saved -> Unit
                is SaveCompanyResult.Invalid -> _uiState.update { it.copy(isSaving = false, errors = result.errors) }
                is SaveCompanyResult.TaxIdTaken -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
