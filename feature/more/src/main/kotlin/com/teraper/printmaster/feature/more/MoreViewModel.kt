package com.teraper.printmaster.feature.more

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

data class MoreUiState(
    val mode: AccountMode? = null,
    val companies: List<Company> = emptyList(),
    val defaultCompany: Company? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(companiesRepository: CompaniesRepository) : ViewModel() {
    val uiState: StateFlow<MoreUiState> = combine(
        companiesRepository.observeProfile(),
        companiesRepository.observeCompanies(),
    ) { profile, companies ->
        MoreUiState(profile?.mode, companies, companies.firstOrNull { it.id == profile?.defaultCompanyId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())
}
