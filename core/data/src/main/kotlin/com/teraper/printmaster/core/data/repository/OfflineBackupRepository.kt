package com.teraper.printmaster.core.data.repository

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.BackupCheck
import com.teraper.printmaster.core.model.BackupSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrintMasterDatabase,
    private val clock: Clock,
    private val analytics: AppAnalytics,
) : BackupRepository {

    // Kept outside the database on purpose: restoring an old backup must not reset it.
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lastBackup = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli))

    private val checkedFile get() = File(context.cacheDir, "restore.db")

    override fun observeLastBackup(): Flow<Instant?> = lastBackup.asStateFlow()

    override fun suggestedFileName(): String = "PrintMaster-${LocalDate.now(clock)}.db"

    override suspend fun saveBackup(uri: String): Boolean = withContext(Dispatchers.IO) {
        val copy = File(context.cacheDir, "backup.db")
        copy.delete()
        try {
            // A consistent copy while the app keeps running; also drops free pages.
            db.openHelper.writableDatabase.execSQL("VACUUM INTO ?", arrayOf<Any>(copy.path))
            val out = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: return@withContext false
            out.use { stream -> copy.inputStream().use { it.copyTo(stream) } }
            val now = clock.instant()
            prefs.edit().putLong(KEY_LAST_BACKUP, now.toEpochMilli()).apply()
            lastBackup.value = now
            analytics.log("backup_saved")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            copy.delete()
        }
    }

    override suspend fun checkBackup(uri: String): BackupCheck = withContext(Dispatchers.IO) {
        val file = checkedFile
        file.delete()
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@withContext BackupCheck.NotABackup
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            if (!file.hasSqliteHeader()) return@withContext BackupCheck.NotABackup.also { file.delete() }
            val result = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { backup ->
                val version = backup.version
                when {
                    version > PrintMasterDatabase.VERSION -> BackupCheck.FromNewerApp
                    version < PrintMasterDatabase.OLDEST_RESTORABLE_VERSION -> BackupCheck.NotABackup
                    // Same version: the tables must be exactly ours, or Room would refuse to open it.
                    version == PrintMasterDatabase.VERSION && identityHash { backup.rawQuery(it, null) } != currentIdentityHash() ->
                        BackupCheck.NotABackup
                    !backup.isDatabaseIntegrityOk -> BackupCheck.NotABackup
                    else -> BackupCheck.Ready(
                        backup = summarize { backup.rawQuery(it, null) },
                        current = summarize { db.openHelper.readableDatabase.query(it) },
                    )
                }
            }
            if (result !is BackupCheck.Ready) file.delete()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            file.delete()
            BackupCheck.NotABackup
        }
    }

    override suspend fun restoreCheckedBackup(): Boolean = withContext(Dispatchers.IO) {
        val checked = checkedFile
        if (!checked.exists()) return@withContext false
        val target = context.getDatabasePath(PrintMasterDatabase.DATABASE_NAME)
        // Copy next to the database first, so the swap below is a rename and can't stop halfway.
        val incoming = File(target.parentFile, "${target.name}.incoming")
        checked.copyTo(incoming, overwrite = true)
        db.close()
        listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        val swapped = incoming.renameTo(target)
        checked.delete()
        swapped
    }

    override suspend fun discardCheckedBackup() {
        withContext(Dispatchers.IO) { checkedFile.delete() }
    }

    private fun currentIdentityHash(): String? = identityHash { db.openHelper.readableDatabase.query(it) }

    private fun identityHash(query: (String) -> Cursor): String? =
        query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { if (it.moveToFirst()) it.getString(0) else null }

    private fun summarize(query: (String) -> Cursor): BackupSummary {
        fun number(sql: String): Long? = query(sql).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
        val companies = query("SELECT name FROM companies ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val lastChange = number(
            """
            SELECT MAX(t) FROM (
              SELECT MAX(created_at) AS t FROM clients UNION ALL
              SELECT MAX(created_at) FROM orders UNION ALL
              SELECT MAX(created_at) FROM charges UNION ALL
              SELECT MAX(created_at) FROM payments
            )
            """,
        )
        return BackupSummary(
            companies = companies,
            clients = number("SELECT COUNT(*) FROM clients")?.toInt() ?: 0,
            orders = number("SELECT COUNT(*) FROM orders")?.toInt() ?: 0,
            moneyEntries = number("SELECT (SELECT COUNT(*) FROM charges) + (SELECT COUNT(*) FROM payments)")?.toInt() ?: 0,
            lastChange = lastChange?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() },
        )
    }

    private fun File.hasSqliteHeader(): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private companion object {
        const val PREFS = "backup"
        const val KEY_LAST_BACKUP = "last_backup"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
