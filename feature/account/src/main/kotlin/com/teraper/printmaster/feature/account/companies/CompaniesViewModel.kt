package com.teraper.printmaster.feature.account.companies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CompaniesUiState(
    val isLoading: Boolean = true,
    val companies: List<Company> = emptyList(),
    val defaultCompanyId: Long = 0,
    val activeCompanyId: Long = 0,
    /** Only a master adds companies; a company has just itself. */
    val canAdd: Boolean = false,
)

@HiltViewModel
class CompaniesViewModel @Inject constructor(
    private val companiesRepository: CompaniesRepository,
) : ViewModel() {

    val uiState: StateFlow<CompaniesUiState> = combine(
        companiesRepository.observeProfile(),
        companiesRepository.observeCompanies(),
        companiesRepository.observeActiveCompany(),
    ) { profile, companies, active ->
        CompaniesUiState(
            isLoading = false,
            companies = companies,
            defaultCompanyId = profile?.defaultCompanyId ?: 0,
            activeCompanyId = active?.id ?: 0,
            canAdd = profile?.mode == AccountMode.MASTER,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompaniesUiState())
}
