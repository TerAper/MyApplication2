package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.model.SharedChanges

/**
 * The shared space of one company (Firebase in the app, an in-memory fake in tests).
 * It decides what each member may see: a master only his own orders and their clients.
 */
interface SyncBackend {

    /** This phone's account id in the shared space; null = not signed in / not joined. */
    val myUid: String?

    suspend fun push(changes: SharedChanges)

    /**
     * What others changed since [cursor] (null = everything this member may see).
     * Returns the changes and the cursor to pass next time.
     */
    suspend fun pull(cursor: String?): Pair<SharedChanges, String?>
}
