package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.CallFolderStatus
import com.teraper.printmaster.core.model.CallRecording
import kotlinx.coroutines.flow.Flow

/** Call recordings from the phone's folder, matched to clients by phone number or contact name. */
interface CallRecordingsRepository {

    fun observeStatus(): Flow<CallFolderStatus>

    /** Every recording, newest first. */
    fun observeRecordings(): Flow<List<CallRecording>>

    fun observeClientRecordings(clientId: Long): Flow<List<CallRecording>>

    /** Starts watching the folder the user picked (a document-tree URI). False if it can't be read. */
    suspend fun setFolder(treeUri: String): Boolean

    /** Looks for new recordings and matches every unmatched one again. */
    suspend fun scan(): ScanResult

    /** Call after the contacts permission was granted or refused. */
    fun onContactsPermissionChanged()

    suspend fun attach(recordingId: Long, clientId: Long)

    suspend fun detach(recordingId: Long, clientId: Long)

    suspend fun setIgnored(recordingId: Long, ignored: Boolean)
}

data class ScanResult(val added: Int = 0, val matched: Int = 0, val folderMissing: Boolean = false)
