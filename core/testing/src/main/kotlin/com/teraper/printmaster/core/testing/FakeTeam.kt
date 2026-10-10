package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.sync.SyncController
import com.teraper.printmaster.core.data.sync.SyncStatus
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.SyncReport
import com.teraper.printmaster.core.model.TeamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory sharing on top of [companies]: [validCode] is another owner's code that attaches
 * the company [otherCompany]; invites get [inviteCode].
 */
class FakeTeamRepository(
    available: Boolean = true,
    private val companies: FakeCompaniesRepository? = null,
    private val validCode: String = "K7PQ2MXA",
    private val otherCompany: String = "Xerox",
    private val otherOwner: String = "Apo",
    private val inviteCode: String = "INVITE22",
) : TeamRepository {
    val state = MutableStateFlow(TeamState(available = available))
    var signInWorks = true
    var internetWorks = true
    val removed = mutableListOf<Pair<Long, Long>>()

    override fun observeState(): Flow<TeamState> = state

    override val googleClientId: String = "client-id"

    override suspend fun signIn(idToken: String): Boolean {
        if (!signInWorks) return false
        state.value = state.value.copy(email = "me@gmail.com", displayName = "Armen Petrosyan")
        return true
    }

    override suspend fun signOut() {
        state.value = state.value.copy(email = null, displayName = null)
    }

    override suspend fun inviteCode(companyId: Long): String? {
        if (!internetWorks) return null
        companies?.companies?.update { list -> list.map { if (it.id == companyId) it.copy(joinCode = inviteCode) else it } }
        return inviteCode
    }

    override suspend fun newJoinCode(companyId: Long): String? {
        if (!internetWorks) return null
        companies?.companies?.update { list -> list.map { if (it.id == companyId) it.copy(joinCode = "NEWCODE2") else it } }
        return "NEWCODE2"
    }

    override suspend fun removeMember(companyId: Long, masterId: Long): Boolean {
        removed += companyId to masterId
        return internetWorks
    }

    override suspend fun attachCompany(code: String, myName: String): AttachResult = when {
        state.value.email == null -> AttachResult.NotSignedIn
        !internetWorks -> AttachResult.Failed
        code == inviteCode -> AttachResult.OwnCompany
        code != validCode -> AttachResult.WrongCode
        companies?.attached?.value?.any { it.name == otherCompany } == true -> AttachResult.AlreadyAttached
        else -> {
            companies?.attached?.update { it + Company(100, otherCompany, kind = CompanyKind.ATTACHED, ownerName = otherOwner, isShared = true) }
            AttachResult.Attached(otherCompany, otherOwner)
        }
    }

    override suspend fun leaveCompany(companyId: Long): Boolean {
        if (!internetWorks) return false
        companies?.attached?.update { list -> list.map { if (it.id == companyId) it.copy(isShared = false) else it } }
        return true
    }
}

class FakeSyncController : SyncController {
    var syncs = 0
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus())

    override suspend fun syncNow(): SyncReport {
        syncs++
        return SyncReport()
    }
}
