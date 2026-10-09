package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.ScanResult
import com.teraper.printmaster.core.model.CallFolderStatus
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.RecordingClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory recordings; attach/ignore change [recordings], scans are counted. */
class FakeCallRecordingsRepository(initial: List<CallRecording> = emptyList()) : CallRecordingsRepository {
    val recordings = MutableStateFlow(initial)
    val folder = MutableStateFlow<String?>(null)
    var folderReadable = true
    var scans = 0

    override fun observeStatus(): Flow<CallFolderStatus> = combine(folder, recordings) { folder, list ->
        CallFolderStatus(folder, canReadContacts = true, total = list.size, unmatched = list.count { !it.isMatched && !it.ignored })
    }

    override fun observeRecordings(): Flow<List<CallRecording>> = recordings

    override fun observeClientRecordings(clientId: Long): Flow<List<CallRecording>> =
        recordings.map { list -> list.filter { r -> r.clients.any { it.id == clientId } } }

    override suspend fun setFolder(treeUri: String): Boolean {
        if (!folderReadable) return false
        folder.value = treeUri
        return true
    }

    override suspend fun scan(): ScanResult {
        scans++
        return ScanResult()
    }

    override fun onContactsPermissionChanged() = Unit

    override suspend fun attach(recordingId: Long, clientId: Long) = update(recordingId) {
        it.copy(clients = it.clients + RecordingClient(clientId, "Client $clientId", manual = true))
    }

    override suspend fun detach(recordingId: Long, clientId: Long) = update(recordingId) {
        it.copy(clients = it.clients.filterNot { c -> c.id == clientId })
    }

    override suspend fun setIgnored(recordingId: Long, ignored: Boolean) = update(recordingId) { it.copy(ignored = ignored) }

    private fun update(id: Long, change: (CallRecording) -> CallRecording) {
        recordings.value = recordings.value.map { if (it.id == id) change(it) else it }
    }
}
