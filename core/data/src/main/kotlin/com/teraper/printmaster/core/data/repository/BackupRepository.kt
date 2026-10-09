package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.BackupCheck
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * The whole database as one file. There is no server, so this is the only way
 * to keep the data when the phone is lost or replaced.
 */
interface BackupRepository {

    /** When a backup was last saved from this phone; null = never. */
    fun observeLastBackup(): Flow<Instant?>

    /** "PrintMaster-2026-10-09.db" */
    fun suggestedFileName(): String

    /** Writes a backup to [uri] (a document the user picked). False if it could not be written. */
    suspend fun saveBackup(uri: String): Boolean

    /** Copies the file at [uri] aside and checks it; nothing on the phone changes yet. */
    suspend fun checkBackup(uri: String): BackupCheck

    /**
     * Replaces all data with the file checked last. The database is closed afterwards,
     * so the app must restart right away. False if there was nothing to restore.
     */
    suspend fun restoreCheckedBackup(): Boolean

    /** Forgets the checked file (the user changed their mind). */
    suspend fun discardCheckedBackup()
}
