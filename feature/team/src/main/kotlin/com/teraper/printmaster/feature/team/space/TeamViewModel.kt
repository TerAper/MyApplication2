package com.teraper.printmaster.feature.team.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.sync.SyncController
import com.teraper.printmaster.core.data.sync.SyncStatus
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.TeamMember
import com.teraper.printmaster.core.model.TeamState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TeamError { SIGN_IN, CREATE, NEW_CODE }

data class TeamUiState(
    val isLoading: Boolean = true,
    val team: TeamState = TeamState(),
    val masters: List<Master> = emptyList(),
    val sync: SyncStatus = SyncStatus(),
    val busy: Boolean = false,
    val error: TeamError? = null,
    /** Choosing which master a joined member is. */
    val linking: TeamMember? = null,
    val confirmRemove: TeamMember? = null,
)

private data class Local(val busy: Boolean = false, val error: TeamError? = null, val linking: TeamMember? = null, val confirmRemove: TeamMember? = null)

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val team: TeamRepository,
    private val syncRunner: SyncController,
    companies: CompaniesRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val googleClientId: String? get() = team.googleClientId

    val uiState: StateFlow<TeamUiState> = combine(team.observeState(), companies.observeMasters(), syncRunner.status, local) { team, masters, sync, local ->
        TeamUiState(false, team, masters, sync, local.busy, local.error, local.linking, local.confirmRemove)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamUiState())

    fun onSignInResult(idToken: String?) {
        if (idToken == null) {
            local.update { it.copy(error = TeamError.SIGN_IN) }
            return
        }
        work(TeamError.SIGN_IN) { team.signIn(idToken) }
    }

    fun onCreateSpace() = work(TeamError.CREATE) { team.createSpace().also { if (it) syncRunner.syncNow() } }

    fun onNewCode() = work(TeamError.NEW_CODE) { team.newJoinCode() }

    fun onLinkClick(member: TeamMember) = local.update { it.copy(linking = member) }

    fun onDismissLink() = local.update { it.copy(linking = null) }

    /** [masterId] null = add as a new master. */
    fun onLinkTo(masterId: Long?) {
        val member = local.value.linking ?: return
        local.update { it.copy(linking = null) }
        viewModelScope.launch {
            team.linkMember(member.uid, masterId)
            syncRunner.syncNow()
        }
    }

    fun onRemoveClick(member: TeamMember) = local.update { it.copy(confirmRemove = member) }

    fun onDismissRemove() = local.update { it.copy(confirmRemove = null) }

    fun onConfirmRemove() {
        val member = local.value.confirmRemove ?: return
        local.update { it.copy(confirmRemove = null) }
        viewModelScope.launch { team.removeMember(member.uid) }
    }

    fun onSyncNow() {
        viewModelScope.launch { syncRunner.syncNow() }
    }

    fun onSignOut() {
        viewModelScope.launch { team.signOut() }
    }

    fun onDismissError() = local.update { it.copy(error = null) }

    private fun work(error: TeamError, block: suspend () -> Boolean) {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update { it.copy(busy = true, error = null) }
            val ok = block()
            local.update { it.copy(busy = false, error = if (ok) null else error) }
        }
    }
}
