package com.teraper.printmaster.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.BackupRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.Company
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MoreUiState(
    val mode: AccountMode? = null,
    val companies: List<Company> = emptyList(),
    val defaultCompany: Company? = null,
    /** When the data was last backed up; null = never. */
    val lastBackup: LocalDate? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    companiesRepository: CompaniesRepository,
    backupRepository: BackupRepository,
    clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<MoreUiState> = combine(
        companiesRepository.observeProfile(),
        companiesRepository.observeCompanies(),
        backupRepository.observeLastBackup(),
    ) { profile, companies, lastBackup ->
        MoreUiState(
            profile?.mode,
            companies,
            companies.firstOrNull { it.id == profile?.defaultCompanyId },
            lastBackup?.atZone(clock.zone)?.toLocalDate(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())
}
