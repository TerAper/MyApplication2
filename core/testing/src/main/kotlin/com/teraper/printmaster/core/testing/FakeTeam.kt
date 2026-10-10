package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.sync.SyncController
import com.teraper.printmaster.core.data.sync.SyncStatus
import com.teraper.printmaster.core.data.team.TeamRepository
import com.teraper.printmaster.core.model.JoinResult
import com.teraper.printmaster.core.model.SyncReport
import com.teraper.printmaster.core.model.TeamMember
import com.teraper.printmaster.core.model.TeamSpace
import com.teraper.printmaster.core.model.TeamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory team: [validCode] joins the space named [spaceName]. */
class FakeTeamRepository(
    available: Boolean = true,
    private val validCode: String = "K7PQ2MXA",
    private val spaceName: String = "Alfa",
) : TeamRepository {
    val state = MutableStateFlow(TeamState(available = available))
    var signInWorks = true
    val linked = mutableListOf<Pair<String, Long?>>()
    val removed = mutableListOf<String>()

    override fun observeState(): Flow<TeamState> = state

    override val googleClientId: String = "client-id"

    override suspend fun signIn(idToken: String): Boolean {
        if (!signInWorks) return false
        state.value = state.value.copy(email = "me@gmail.com")
        return true
    }

    override suspend fun signOut() {
        state.value = state.value.copy(email = null)
    }

    override suspend fun createSpace(): Boolean {
        state.value = state.value.copy(space = TeamSpace("w1", spaceName, isOwner = true, joinCode = validCode))
        return true
    }

    override suspend fun newJoinCode(): Boolean {
        state.value = state.value.copy(space = state.value.space?.copy(joinCode = "NEWCODE2"))
        return true
    }

    override suspend fun join(code: String, myName: String): JoinResult = when {
        state.value.email == null -> JoinResult.NOT_SIGNED_IN
        code != validCode -> JoinResult.WRONG_CODE
        else -> {
            state.value = state.value.copy(space = TeamSpace("w1", spaceName, isOwner = false, joinCode = null))
            JoinResult.JOINED
        }
    }

    override suspend fun linkMember(uid: String, masterId: Long?) {
        linked += uid to masterId
        state.value = state.value.copy(members = state.value.members.map { if (it.uid == uid) it.copy(masterId = masterId ?: 99) else it })
    }

    override suspend fun removeMember(uid: String) {
        removed += uid
        state.value = state.value.copy(members = state.value.members.filterNot { it.uid == uid })
    }

    fun addMember(member: TeamMember) {
        state.value = state.value.copy(members = state.value.members + member)
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
