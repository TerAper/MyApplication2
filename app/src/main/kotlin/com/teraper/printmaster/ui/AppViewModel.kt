package com.teraper.printmaster.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppUiState {
    data object Loading : AppUiState

    /** Everyone starts by signing in with Google (only when this build has Firebase). */
    data class NeedsSignIn(val canSkipForTest: Boolean) : AppUiState

    /** Signed in, nothing registered yet: the user's name and first company. */
    data object NeedsRegistration : AppUiState

    data class Ready(
        val activeCompany: Company?,
        val companies: List<Company>,
        val mode: AccountMode = AccountMode.OWNER,
        /** Attached to another owner's company: its orders come to this phone. */
        val worksForOthers: Boolean = false,
    ) : AppUiState {
        /** The switch strip is only worth its space with two or more companies. */
        val showCompanySwitch: Boolean get() = companies.size > 1 && activeCompany != null
    }
}

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val companiesRepository: CompaniesRepository,
    team: TeamRepository,
    private val analytics: AppAnalytics,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** A test build on an emulator, which has no Google account to sign in with. */
    private val canSkipForTest = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
        (Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.PRODUCT.startsWith("sdk"))
    private val skipped = MutableStateFlow(canSkipForTest && prefs.getBoolean(KEY_SKIPPED, false))

    val uiState: StateFlow<AppUiState> = combine(
        team.observeState(),
        skipped,
        companiesRepository.observeProfile(),
        combine(companiesRepository.observeCompanies(), companiesRepository.observeAttachedCompanies(), ::Pair),
        companiesRepository.observeActiveCompany(),
    ) { account, skipped, profile, (companies, attached), active ->
        when {
            account.available && account.email == null && !skipped -> AppUiState.NeedsSignIn(canSkipForTest)
            // A phone that only joined a company (older app) adds its own company now.
            profile == null || profile.mode == AccountMode.JOINED -> AppUiState.NeedsRegistration
            else -> {
                // Lets usage be split by kind of user; nothing personal.
                analytics.setUserProperty("mode", profile.mode.name)
                AppUiState.Ready(active, companies, profile.mode, worksForOthers = attached.any { it.isShared })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState.Loading)

    fun onCompanySelected(id: Long) = companiesRepository.selectCompany(id)

    fun onSkipSignInForTest() {
        if (!canSkipForTest) return
        prefs.edit { putBoolean(KEY_SKIPPED, true) }
        skipped.value = true
    }

    private companion object {
        const val KEY_SKIPPED = "test_skipped_sign_in"
    }
}
