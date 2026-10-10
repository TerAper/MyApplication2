package com.teraper.printmaster.core.data.team

import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.TeamState
import kotlinx.coroutines.flow.Flow

/**
 * The Google account and the shared spaces: own companies invite masters with a code,
 * and the user attaches to other owners' companies with their codes.
 */
interface TeamRepository {

    fun observeState(): Flow<TeamState>

    /** Server client id for "Sign in with Google"; null when the Firebase file lacks it. */
    val googleClientId: String?

    /** Finishes sign-in with the Google ID token the sign-in sheet returned. */
    suspend fun signIn(idToken: String): Boolean

    suspend fun signOut()

    /** Own company: the code masters enter to attach; its shared space is made the first time. Null = failed. */
    suspend fun inviteCode(companyId: Long): String?

    /** Own company: a new code; the old one stops working (attached masters stay). */
    suspend fun newJoinCode(companyId: Long): String?

    /** Own company: the master gets no more of its orders. */
    suspend fun removeMember(companyId: Long, masterId: Long): Boolean

    /** Another owner's code: their company appears as attached and starts giving orders. */
    suspend fun attachCompany(code: String, myName: String): AttachResult

    /** Stops working for an attached company; the orders done for it stay as history. */
    suspend fun leaveCompany(companyId: Long): Boolean
}
