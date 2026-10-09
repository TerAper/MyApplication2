package com.teraper.printmaster.feature.reports.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.DebtReportLabels
import com.teraper.printmaster.core.data.repository.ExportRepository
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DebtExportScope { DEBTORS, ALL }

data class DebtExportUiState(
    val company: Company? = null,
    val scope: DebtExportScope = DebtExportScope.DEBTORS,
    val debtorCount: Int = 0,
    val clientCount: Int = 0,
    val totalDebt: Money = Money.ZERO,
    val isWriting: Boolean = false,
    val dialog: DebtExportDialog? = null,
) {
    val rowCount: Int get() = if (scope == DebtExportScope.DEBTORS) debtorCount else clientCount
}

enum class DebtExportDialog { SAVED, FAILED }

sealed interface DebtExportEvent {
    /** Open the share sheet for the file at [path]. */
    data class Share(val path: String) : DebtExportEvent

    /** Ask where to save; [fileName] is the suggestion. */
    data class PickSaveLocation(val fileName: String) : DebtExportEvent
}

@HiltViewModel
class DebtExportViewModel @Inject constructor(
    clientsRepository: ClientsRepository,
    companiesRepository: CompaniesRepository,
    private val exportRepository: ExportRepository,
) : ViewModel() {

    private val local = MutableStateFlow(DebtExportUiState())

    private val _events = Channel<DebtExportEvent>(Channel.BUFFERED)
    val events: Flow<DebtExportEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<DebtExportUiState> = combine(
        local,
        clientsRepository.observeClientSummaries(),
        companiesRepository.observeActiveCompany(),
    ) { state, clients, company ->
        val debtors = clients.filter { it.balance.isPositive }
        state.copy(
            company = company,
            debtorCount = debtors.size,
            clientCount = clients.size,
            totalDebt = debtors.map { it.balance }.sum(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtExportUiState())

    private val onlyDebtors get() = local.value.scope == DebtExportScope.DEBTORS

    fun onScopeChange(scope: DebtExportScope) = local.update { it.copy(scope = scope) }

    fun onSaveClick() {
        viewModelScope.launch { _events.send(DebtExportEvent.PickSaveLocation(exportRepository.debtReportFileName())) }
    }

    /** [uri] null = the user closed the file picker. */
    fun onSaveTo(uri: String?, labels: DebtReportLabels) {
        if (uri == null || local.value.isWriting) return
        viewModelScope.launch {
            local.update { it.copy(isWriting = true) }
            val saved = exportRepository.saveDebtReport(uri, onlyDebtors, labels)
            local.update { it.copy(isWriting = false, dialog = if (saved) DebtExportDialog.SAVED else DebtExportDialog.FAILED) }
        }
    }

    fun onShare(labels: DebtReportLabels) {
        if (local.value.isWriting) return
        viewModelScope.launch {
            local.update { it.copy(isWriting = true) }
            val path = exportRepository.cacheDebtReport(onlyDebtors, labels)
            local.update { it.copy(isWriting = false, dialog = if (path == null) DebtExportDialog.FAILED else null) }
            if (path != null) _events.send(DebtExportEvent.Share(path))
        }
    }

    fun onDismissDialog() = local.update { it.copy(dialog = null) }
}
