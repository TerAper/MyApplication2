package com.teraper.printmaster.feature.team.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.sync.SyncController
import com.teraper.printmaster.core.data.sync.SyncStatus
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.TeamState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TeamError { SIGN_IN, INVITE, NEW_CODE, REMOVE, LEAVE }

/** An own company and the masters attached to it. */
data class OwnCompanySharing(val company: Company, val masters: List<Master>)

/** "Enter a code" dialog. */
data class AttachForm(
    val code: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val codeTooShort: Boolean = false,
    val nameMissing: Boolean = false,
    /** The last attempt's answer when it didn't attach. */
    val failure: AttachResult? = null,
)

data class TeamUiState(
    val isLoading: Boolean = true,
    val team: TeamState = TeamState(),
    val own: List<OwnCompanySharing> = emptyList(),
    val attached: List<Company> = emptyList(),
    val sync: SyncStatus = SyncStatus(),
    /** The company an invite or new code is being made for. */
    val busyCompanyId: Long? = null,
    val error: TeamError? = null,
    val attach: AttachForm? = null,
    /** "Attached: X of Apo" after entering a code. */
    val justAttached: AttachResult.Attached? = null,
    val confirmRemove: Pair<Company, Master>? = null,
    val confirmLeave: Company? = null,
) {
    val sharesAnything: Boolean get() = attached.any { it.isShared } || own.any { it.company.joinCode != null }
}

private data class Local(
    val busyCompanyId: Long? = null,
    val error: TeamError? = null,
    val attach: AttachForm? = null,
    val justAttached: AttachResult.Attached? = null,
    val confirmRemove: Pair<Company, Master>? = null,
    val confirmLeave: Company? = null,
)

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val team: TeamRepository,
    private val syncRunner: SyncController,
    private val companies: CompaniesRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val googleClientId: String? get() = team.googleClientId

    val uiState: StateFlow<TeamUiState> = combine(
        team.observeState(),
        combine(companies.observeCompanies(), companies.observeMasters(), companies.observeAttachedCompanies(), ::Triple),
        syncRunner.status,
        local,
    ) { team, (own, masters, attached), sync, local ->
        TeamUiState(
            isLoading = false,
            team = team,
            own = own.map { company -> OwnCompanySharing(company, masters.filter { it.isAttached && company.id in it.companyIds }) },
            attached = attached,
            sync = sync,
            busyCompanyId = local.busyCompanyId,
            error = local.error,
            attach = local.attach,
            justAttached = local.justAttached,
            confirmRemove = local.confirmRemove,
            confirmLeave = local.confirmLeave,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamUiState())

    fun onSignInResult(idToken: String?) {
        if (idToken == null) {
            local.update { it.copy(error = TeamError.SIGN_IN) }
            return
        }
        viewModelScope.launch { if (!team.signIn(idToken)) local.update { it.copy(error = TeamError.SIGN_IN) } }
    }

    fun onSignOut() {
        viewModelScope.launch { team.signOut() }
    }

    /** Own company: makes its code (and shared space) the first time. */
    fun onInvite(companyId: Long) = companyWork(companyId, TeamError.INVITE) { team.inviteCode(companyId) != null }

    fun onNewCode(companyId: Long) = companyWork(companyId, TeamError.NEW_CODE) { team.newJoinCode(companyId) != null }

    fun onRemoveClick(company: Company, master: Master) = local.update { it.copy(confirmRemove = company to master) }

    fun onDismissRemove() = local.update { it.copy(confirmRemove = null) }

    fun onConfirmRemove() {
        val (company, master) = local.value.confirmRemove ?: return
        local.update { it.copy(confirmRemove = null) }
        companyWork(company.id, TeamError.REMOVE) { team.removeMember(company.id, master.id) }
    }

    // Attaching to another owner's company

    fun onAttachClick() {
        viewModelScope.launch {
            val name = companies.observeProfile().first()?.ownerName.orEmpty()
            local.update { it.copy(attach = AttachForm(name = name), justAttached = null) }
        }
    }

    fun onAttachCodeChange(code: String) = updateAttach {
        it.copy(code = code.filter { c -> c.isLetterOrDigit() }.uppercase().take(CODE_LENGTH), codeTooShort = false, failure = null)
    }

    fun onAttachNameChange(name: String) = updateAttach { it.copy(name = name, nameMissing = false) }

    fun onDismissAttach() = local.update { it.copy(attach = null) }

    fun onConfirmAttach() {
        val form = local.value.attach ?: return
        if (form.busy) return
        val codeTooShort = form.code.length != CODE_LENGTH
        val nameMissing = form.name.isBlank()
        if (codeTooShort || nameMissing) {
            updateAttach { it.copy(codeTooShort = codeTooShort, nameMissing = nameMissing) }
            return
        }
        updateAttach { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            when (val result = team.attachCompany(form.code, form.name)) {
                is AttachResult.Attached -> {
                    local.update { it.copy(attach = null, justAttached = result) }
                    syncRunner.syncNow()
                }
                else -> updateAttach { it.copy(busy = false, failure = result) }
            }
        }
    }

    fun onDismissAttached() = local.update { it.copy(justAttached = null) }

    fun onLeaveClick(company: Company) = local.update { it.copy(confirmLeave = company) }

    fun onDismissLeave() = local.update { it.copy(confirmLeave = null) }

    fun onConfirmLeave() {
        val company = local.value.confirmLeave ?: return
        local.update { it.copy(confirmLeave = null) }
        companyWork(company.id, TeamError.LEAVE) { team.leaveCompany(company.id) }
    }

    fun onSyncNow() {
        viewModelScope.launch { syncRunner.syncNow() }
    }

    fun onDismissError() = local.update { it.copy(error = null) }

    private fun updateAttach(change: (AttachForm) -> AttachForm) = local.update { s -> s.copy(attach = s.attach?.let(change)) }

    private fun companyWork(companyId: Long, error: TeamError, block: suspend () -> Boolean) {
        if (local.value.busyCompanyId != null) return
        viewModelScope.launch {
            local.update { it.copy(busyCompanyId = companyId, error = null) }
            val ok = block()
            local.update { it.copy(busyCompanyId = null, error = if (ok) null else error) }
            if (ok) syncRunner.syncNow()
        }
    }

    private companion object {
        const val CODE_LENGTH = 8
    }
}
