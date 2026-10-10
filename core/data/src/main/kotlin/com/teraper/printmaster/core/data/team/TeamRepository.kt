package com.teraper.printmaster.core.data.team

import com.teraper.printmaster.core.model.JoinResult
import com.teraper.printmaster.core.model.TeamState
import kotlinx.coroutines.flow.Flow

/** Signing in and the company's shared space (create, join code, members). */
interface TeamRepository {

    fun observeState(): Flow<TeamState>

    /** Server client id for "Sign in with Google"; null when the Firebase file lacks it. */
    val googleClientId: String?

    /** Finishes sign-in with the Google ID token the sign-in sheet returned. */
    suspend fun signIn(idToken: String): Boolean

    suspend fun signOut()

    /** Company side: creates the shared space for the active company, with a new join code. */
    suspend fun createSpace(): Boolean

    /** Company side: a new code; the old one stops working (masters who joined stay). */
    suspend fun newJoinCode(): Boolean

    /** Master side: joins with the code the company gave; [myName] is shown to the company. */
    suspend fun join(code: String, myName: String): JoinResult

    /** Company side: this joined master is local master [masterId] (null = create a new master record). */
    suspend fun linkMember(uid: String, masterId: Long?)

    /** Company side: removes a master from the space. */
    suspend fun removeMember(uid: String)
}
