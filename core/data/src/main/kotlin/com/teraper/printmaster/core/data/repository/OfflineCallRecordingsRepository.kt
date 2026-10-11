package com.teraper.printmaster.core.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.teraper.printmaster.core.data.calls.CallRecordingsSync
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CallRecordingDao
import com.teraper.printmaster.core.database.dao.RecordingLinkRow
import com.teraper.printmaster.core.database.entity.CallRecordingClientEntity
import com.teraper.printmaster.core.database.entity.CallRecordingEntity
import com.teraper.printmaster.core.model.CallFolderStatus
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.core.model.RecordingNames
import com.teraper.printmaster.core.model.RecordingMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
internal class OfflineCallRecordingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrintMasterDatabase,
    private val dao: CallRecordingDao,
    private val clock: Clock,
) : CallRecordingsRepository {

    // The folder is this phone's, not the data's: kept out of the database so a restore doesn't carry it.
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val folder = MutableStateFlow(prefs.getString(KEY_FOLDER, null))
    private val contactsAllowed = MutableStateFlow(hasContactsPermission())
    private val scanLock = Mutex()

    override fun observeStatus(): Flow<CallFolderStatus> =
        combine(folder, contactsAllowed, observeRecordings()) { folder, contacts, recordings ->
            CallFolderStatus(
                folderName = folder?.let(::folderName),
                canReadContacts = contacts,
                total = recordings.size,
                unmatched = recordings.count { !it.isMatched && !it.ignored },
            )
        }

    override fun observeRecordings(): Flow<List<CallRecording>> =
        combine(dao.observeRecordings(), dao.observeLinks()) { rows, links -> rows.toModels(links) }

    override fun observeClientRecordings(clientId: Long): Flow<List<CallRecording>> =
        combine(dao.observeClientRecordings(clientId), dao.observeLinks()) { rows, links -> rows.toModels(links) }

    override suspend fun setFolder(treeUri: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(treeUri)
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            return@withContext false
        }
        // Let go of a previously picked folder.
        folder.value?.takeIf { it != treeUri }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.edit().putString(KEY_FOLDER, treeUri).apply()
        folder.value = treeUri
        CallRecordingsSync.schedule(context)
        true
    }

    override fun onContactsPermissionChanged() {
        val allowed = hasContactsPermission()
        val newlyAllowed = allowed && !contactsAllowed.value
        contactsAllowed.value = allowed
        // Recordings named after contacts can be matched now.
        if (newlyAllowed) CallRecordingsSync.schedule(context)
    }

    override suspend fun scan(): ScanResult = read(sinceMillis = null)

    override suspend fun refresh(): ScanResult {
        val last = prefs.getLong(KEY_LAST_LOOK, 0L)
        if (clock.millis() - last < REFRESH_PAUSE_MS) return ScanResult()
        // A file can show up a little after its time (it's written while the call goes on).
        return read(sinceMillis = (last - LOOK_MARGIN_MS).takeIf { last > 0 })
    }

    /** [sinceMillis] null = the whole folder (and forget deleted files); else only files changed after it. */
    private suspend fun read(sinceMillis: Long?): ScanResult = scanLock.withLock {
        withContext(Dispatchers.IO) {
            contactsAllowed.value = hasContactsPermission()
            val tree = folder.value ?: return@withContext ScanResult()
            val startedAt = clock.millis()
            val files = try {
                listFolder(Uri.parse(tree))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Permission taken back, or the folder was removed.
                return@withContext ScanResult(folderMissing = true)
            }
            val result = if (sinceMillis == null) importFiles(files) else importFiles(files.filter { it.modifiedAt > sinceMillis }, forgetMissing = false)
            prefs.edit().putLong(KEY_LAST_LOOK, startedAt).apply()
            result
        }
    }

    override fun startWatching(scope: CoroutineScope) {
        scope.launch {
            // A number or name added to a client: its older recordings are found at once.
            combine(dao.observeClientPhones(), dao.observeClientNames(), ::Pair)
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_000)
                .collect { scanLock.withLock { rematch() } }
        }
    }

    /** Adds recordings not seen before, forgets deleted ones ([forgetMissing], when [files] is the whole folder), then matches. */
    internal suspend fun importFiles(files: List<FolderFile>, forgetMissing: Boolean = true): ScanResult {
        val prefixes = RecordingNames.KNOWN_PREFIXES + listOfNotNull(
            RecordingNames.learnPrefix(files.map { it.name })
                .takeIf { files.none { file -> RecordingNames.KNOWN_PREFIXES.any { file.name.startsWith("$it ", ignoreCase = true) } } },
        )

        val known = dao.getUris().toSet()
        var added = 0
        for (file in files) {
            if (file.uri in known) continue
            val name = RecordingNames.parse(file.name, prefixes) ?: continue
            val id = dao.insertRecording(
                CallRecordingEntity(
                    uri = file.uri,
                    fileName = file.name,
                    caller = name.caller,
                    startedAt = name.startedAt.atZone(clock.zone).toInstant().toEpochMilli(),
                    durationMs = duration(file.uri),
                    createdAt = clock.millis(),
                ),
            )
            if (id > 0) added++
        }
        if (forgetMissing) {
            val listed = files.mapTo(HashSet()) { it.uri }
            (known - listed).chunked(500).forEach { dao.deleteByUris(it) }
        }
        return ScanResult(added = added, matched = rematch())
    }

    /**
     * Matches every recording again (not only new ones): a client may have got a number since,
     * or a contact was saved. What the user attached or took off by hand stays as they left it.
     * Returns how many recordings have a client now.
     */
    private suspend fun rematch(): Int {
        val recordings = dao.getMatchable()
        val matcher = RecordingMatcher(
            clientPhones = dao.getClientPhones().map { it.clientId to it.number },
            clientNames = dao.getClientNames().map { it.clientId to it.number },
            contacts = if (contactsAllowed.value) contacts() else emptyList(),
        )
        val links = dao.getAllLinks().groupBy { it.recordingId }
        val add = ArrayList<CallRecordingClientEntity>()
        val remove = ArrayList<CallRecordingClientEntity>()
        for (recording in recordings) {
            val existing = links[recording.id].orEmpty()
            val wanted = matcher.clientsOf(recording.caller)
            // Automatic links that no longer fit (a number moved to another client) go; the user's stay.
            remove += existing.filter { !it.manual && !it.rejected && it.clientId !in wanted }
            add += wanted.filter { id -> existing.none { it.clientId == id } }.map { CallRecordingClientEntity(recording.id, it, manual = false) }
        }
        db.withTransaction {
            remove.forEach { dao.deleteLink(it.recordingId, it.clientId) }
            dao.insertLinks(add)
        }
        val linked = dao.getAllLinks().filterNot { it.rejected }.mapTo(HashSet()) { it.recordingId }
        return recordings.count { it.id in linked }
    }

    /** Phone book: (name, number) of every contact number. */
    private fun contacts(): List<Pair<String, String>> = try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    add(name to number)
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }

    override suspend fun attach(recordingId: Long, clientId: Long) {
        dao.upsertLink(CallRecordingClientEntity(recordingId, clientId, manual = true))
    }

    /** Kept as taken off, so automatic matching doesn't put it back. */
    override suspend fun detach(recordingId: Long, clientId: Long) = dao.rejectLink(recordingId, clientId)

    override suspend fun setIgnored(recordingId: Long, ignored: Boolean) = dao.setIgnored(recordingId, ignored)

    /** [modifiedAt]: epoch millis the file last changed (0 = unknown, read every time). */
    internal data class FolderFile(val uri: String, val name: String, val modifiedAt: Long = Long.MAX_VALUE)

    private fun listFolder(tree: Uri): List<FolderFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = context.contentResolver.query(children, projection, null, null, null) ?: error("Folder not readable")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val name = it.getString(1) ?: continue
                    val modified = if (it.isNull(3)) Long.MAX_VALUE else it.getLong(3)
                    add(FolderFile(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)).toString(), name, modified))
                }
            }
        }
    }

    private fun duration(uri: String): Long = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    } catch (e: Exception) {
        0L
    }

    /** "primary:Recordings/Call" → "Recordings/Call". */
    private fun folderName(treeUri: String): String =
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).substringAfter(':') }.getOrNull()
            ?.ifEmpty { null } ?: treeUri

    private fun hasContactsPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun List<CallRecordingEntity>.toModels(links: List<RecordingLinkRow>): List<CallRecording> {
        val byRecording = links.groupBy { it.recordingId }
        return map { row ->
            CallRecording(
                id = row.id,
                uri = row.uri,
                fileName = row.fileName,
                caller = row.caller,
                startedAt = Instant.ofEpochMilli(row.startedAt).atZone(clock.zone).toLocalDateTime(),
                durationMillis = row.durationMs,
                clients = byRecording[row.id].orEmpty().map { RecordingClient(it.clientId, it.name, it.manual) },
                ignored = row.ignored,
            )
        }
    }

    private companion object {
        const val PREFS = "call_recordings"
        const val KEY_FOLDER = "folder"
        const val KEY_LAST_LOOK = "last_look"

        /** Opening several client pages in a row doesn't read the folder each time. */
        const val REFRESH_PAUSE_MS = 20_000L
        const val LOOK_MARGIN_MS = 10 * 60_000L
    }
}
