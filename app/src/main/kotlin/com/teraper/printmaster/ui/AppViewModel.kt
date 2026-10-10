package com.teraper.printmaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppUiState {
    data object Loading : AppUiState

    /** First launch: show registration instead of the app. */
    data object NeedsRegistration : AppUiState

    data class Ready(val activeCompany: Company?, val companies: List<Company>, val mode: AccountMode = AccountMode.MASTER) : AppUiState {
        /** The switch strip is only worth its space with two or more companies. */
        val showCompanySwitch: Boolean get() = companies.size > 1 && activeCompany != null
    }
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val companiesRepository: CompaniesRepository,
    private val analytics: AppAnalytics,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        companiesRepository.observeProfile(),
        companiesRepository.observeCompanies(),
        companiesRepository.observeActiveCompany(),
    ) { profile, companies, active ->
        if (profile == null) {
            AppUiState.NeedsRegistration
        } else {
            // Lets usage be split by kind of user (master, company, joined master); nothing personal.
            analytics.setUserProperty("mode", profile.mode.name)
            AppUiState.Ready(active, companies, profile.mode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState.Loading)

    fun onCompanySelected(id: Long) = companiesRepository.selectCompany(id)
}
