package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.model.SharedChanges
import com.teraper.printmaster.core.model.SharedMember

/**
 * The shared spaces (Firebase in the app, an in-memory fake in tests): one per own company that
 * invited masters, and one per attached company. The space decides what each member may see:
 * an attached master only his own orders and their clients.
 */
interface SyncBackend {

    /** This phone's account id; null = not signed in. */
    val myUid: String?

    suspend fun push(spaceId: String, changes: SharedChanges)

    /**
     * What others changed in [spaceId] since [cursor] (null = everything this phone may see there).
     * Returns the changes and the cursor to pass next time.
     */
    suspend fun pull(spaceId: String, asOwner: Boolean, cursor: String?): Pair<SharedChanges, String?>

    /** Owner: who attached to the space with its code. */
    suspend fun members(spaceId: String): List<SharedMember>
}
