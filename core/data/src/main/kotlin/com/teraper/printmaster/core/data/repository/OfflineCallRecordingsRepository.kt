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
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.core.model.RecordingNames
import dagger.hilt.android.qualifiers.ApplicationContext
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
        contactsAllowed.value = hasContactsPermission()
    }

    override suspend fun scan(): ScanResult = scanLock.withLock {
        withContext(Dispatchers.IO) {
            contactsAllowed.value = hasContactsPermission()
            val tree = folder.value ?: return@withContext ScanResult()
            val files = try {
                listFolder(Uri.parse(tree))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Permission taken back, or the folder was removed.
                return@withContext ScanResult(folderMissing = true)
            }
            importFiles(files)
        }
    }

    /** Adds recordings not seen before, forgets deleted ones, then matches. [files] = the whole folder. */
    internal suspend fun importFiles(files: List<FolderFile>): ScanResult {
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
        val listed = files.mapTo(HashSet()) { it.uri }
        (known - listed).chunked(500).forEach { dao.deleteByUris(it) }

        return ScanResult(added = added, matched = matchUnmatched())
    }

    /** Matches every recording without a client: by its number, or by the contact's numbers, or by the client's name. */
    private suspend fun matchUnmatched(): Int {
        val unmatched = dao.getUnmatched()
        if (unmatched.isEmpty()) return 0
        val clientsByPhone = dao.getClientPhones().groupBy({ ClientSearch.normalizePhone(it.number) }, { it.clientId })
        val clientsByName = dao.getClientNames().groupBy({ ClientSearch.normalizeText(it.number) }, { it.clientId })
        val contacts = if (contactsAllowed.value) contactNumbers() else emptyMap()

        val links = unmatched.flatMap { recording ->
            val caller = ClientSearch.normalizeText(recording.caller)
            val phones = RecordingNames.phoneOf(recording.caller)?.let(::listOf) ?: contacts[caller].orEmpty()
            val clientIds = phones.flatMap { clientsByPhone[it].orEmpty() }.toSet()
                .ifEmpty { clientsByName[caller].orEmpty().toSet() }
            clientIds.map { CallRecordingClientEntity(recording.id, it, manual = false) }
        }
        db.withTransaction { dao.insertLinks(links) }
        return links.distinctBy { it.recordingId }.size
    }

    /** Phone-book names (normalized) → their numbers (normalized). */
    private fun contactNumbers(): Map<String, List<String>> = try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            buildMap<String, MutableList<String>> {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    getOrPut(ClientSearch.normalizeText(name)) { mutableListOf() } += ClientSearch.normalizePhone(number)
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        emptyMap()
    }

    override suspend fun attach(recordingId: Long, clientId: Long) {
        dao.insertLinks(listOf(CallRecordingClientEntity(recordingId, clientId, manual = true)))
    }

    override suspend fun detach(recordingId: Long, clientId: Long) = dao.deleteLink(recordingId, clientId)

    override suspend fun setIgnored(recordingId: Long, ignored: Boolean) = dao.setIgnored(recordingId, ignored)

    internal data class FolderFile(val uri: String, val name: String)

    private fun listFolder(tree: Uri): List<FolderFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = context.contentResolver.query(children, projection, null, null, null) ?: error("Folder not readable")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    val name = it.getString(1) ?: continue
                    add(FolderFile(DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)).toString(), name))
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
    }
}
