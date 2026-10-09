package com.teraper.printmaster.feature.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.BackupRepository
import com.teraper.printmaster.core.model.BackupCheck
import com.teraper.printmaster.core.model.BackupSummary
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

enum class BackupWork { SAVING, CHECKING, RESTORING }

data class BackupUiState(
    val lastBackup: Instant? = null,
    /** No backup, or the last one is more than a week old. */
    val needsBackup: Boolean = true,
    val work: BackupWork? = null,
    val dialog: BackupDialog? = null,
)

sealed interface BackupDialog {
    data object Saved : BackupDialog
    data object SaveFailed : BackupDialog
    data object NotABackup : BackupDialog
    data object FromNewerApp : BackupDialog

    /** Last question before all data is replaced. */
    data class ConfirmRestore(val backup: BackupSummary, val current: BackupSummary) : BackupDialog
}

sealed interface BackupEvent {
    /** The data was replaced; the app has to start again to open it. */
    data object RestartApp : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(BackupUiState())

    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<BackupUiState> = combine(local, repository.observeLastBackup()) { state, last ->
        state.copy(lastBackup = last, needsBackup = last == null || Duration.between(last, clock.instant()) > REMIND_AFTER)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(): String = repository.suggestedFileName()

    /** [uri] null = the user closed the file picker. */
    fun onSaveTo(uri: String?) {
        if (uri == null || local.value.work != null) return
        viewModelScope.launch {
            local.update { it.copy(work = BackupWork.SAVING) }
            val saved = repository.saveBackup(uri)
            local.update { it.copy(work = null, dialog = if (saved) BackupDialog.Saved else BackupDialog.SaveFailed) }
        }
    }

    fun onRestoreFrom(uri: String?) {
        if (uri == null || local.value.work != null) return
        viewModelScope.launch {
            local.update { it.copy(work = BackupWork.CHECKING) }
            val dialog = when (val check = repository.checkBackup(uri)) {
                is BackupCheck.Ready -> BackupDialog.ConfirmRestore(check.backup, check.current)
                BackupCheck.NotABackup -> BackupDialog.NotABackup
                BackupCheck.FromNewerApp -> BackupDialog.FromNewerApp
            }
            local.update { it.copy(work = null, dialog = dialog) }
        }
    }

    fun onConfirmRestore() {
        if (local.value.dialog !is BackupDialog.ConfirmRestore) return
        viewModelScope.launch {
            local.update { it.copy(work = BackupWork.RESTORING, dialog = null) }
            if (repository.restoreCheckedBackup()) {
                _events.send(BackupEvent.RestartApp)
            } else {
                local.update { it.copy(work = null, dialog = BackupDialog.NotABackup) }
            }
        }
    }

    fun onDismissDialog() {
        val wasRestoreQuestion = local.value.dialog is BackupDialog.ConfirmRestore
        local.update { it.copy(dialog = null) }
        if (wasRestoreQuestion) viewModelScope.launch { repository.discardCheckedBackup() }
    }

    private companion object {
        val REMIND_AFTER: Duration = Duration.ofDays(7)
    }
}
