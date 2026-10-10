package com.teraper.printmaster.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.BackupCheck
import com.teraper.printmaster.core.model.ClientDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OfflineBackupRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = TestRepos()
    private val backup = repos.backup
    private val dir = File(context.cacheDir, "backup-test").apply { mkdirs() }
    private val restoredDb = context.getDatabasePath(PrintMasterDatabase.DATABASE_NAME)

    @After
    fun tearDown() {
        repos.db.close()
        dir.deleteRecursively()
        context.deleteDatabase(PrintMasterDatabase.DATABASE_NAME)
    }

    private fun uri(file: File) = Uri.fromFile(file).toString()

    private suspend fun savedBackup(): File {
        repos.register("Alfa")
        repos.clients.saveClient(ClientDraft(name = "Firm"))
        val file = File(dir, backup.suggestedFileName())
        assertTrue(backup.saveBackup(uri(file)))
        return file
    }

    @Test
    fun savedBackupIsCheckedAndSummarized() = runTest {
        val file = savedBackup()
        assertEquals("PrintMaster-2026-10-07.zip", file.name)
        assertEquals(Instant.parse("2026-10-07T10:00:00Z"), backup.observeLastBackup().first())

        val check = backup.checkBackup(uri(file)) as BackupCheck.Ready
        assertEquals(listOf("Alfa"), check.backup.companies)
        assertEquals(1, check.backup.clients)
        assertEquals(LocalDate.of(2026, 10, 7), check.backup.lastChange)
        assertEquals(check.backup, check.current)
    }

    @Test
    fun otherFilesAreRefused() = runTest {
        val text = File(dir, "notes.db").apply { writeText("hello") }
        assertEquals(BackupCheck.NotABackup, backup.checkBackup(uri(text)))

        val foreign = File(dir, "foreign.db")
        SQLiteDatabase.openOrCreateDatabase(foreign, null).use { it.execSQL("CREATE TABLE t (x)"); it.version = PrintMasterDatabase.VERSION }
        assertEquals(BackupCheck.NotABackup, backup.checkBackup(uri(foreign)))

        val newer = File(dir, "newer.db")
        SQLiteDatabase.openOrCreateDatabase(newer, null).use { it.version = PrintMasterDatabase.VERSION + 1 }
        assertEquals(BackupCheck.FromNewerApp, backup.checkBackup(uri(newer)))

        assertFalse(backup.restoreCheckedBackup())
    }

    @Test
    fun restoreReplacesTheDatabaseFile() = runTest {
        val file = savedBackup()
        assertTrue(backup.checkBackup(uri(file)) is BackupCheck.Ready)
        assertTrue(backup.restoreCheckedBackup())

        SQLiteDatabase.openDatabase(restoredDb.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM clients", null).use { it.moveToFirst(); assertEquals("Firm", it.getString(0)) }
        }
        // The checked copy is used up.
        assertFalse(backup.restoreCheckedBackup())
    }

    @Test
    fun photosTravelInTheBackupAndOldDbBackupsStillWork() = runTest {
        val photos = File(context.filesDir, "photos").apply { deleteRecursively(); mkdirs() }
        File(photos, "abc.webp").writeText("full")
        File(photos, "abc_t.webp").writeText("thumb")
        val file = savedBackup()

        // Photos changed after the backup: restoring brings back the backed-up ones.
        File(photos, "abc.webp").delete()
        File(photos, "new.webp").writeText("later")
        assertTrue(backup.checkBackup(uri(file)) is BackupCheck.Ready)
        assertTrue(backup.restoreCheckedBackup())
        assertEquals(setOf("abc.webp", "abc_t.webp"), photos.listFiles()!!.map { it.name }.toSet())
        assertEquals("full", File(photos, "abc.webp").readText())

        // A plain .db file from before photos existed is still accepted.
        val plainDb = File(dir, "old.db")
        java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "printmaster.db") plainDb.outputStream().use { zip.copyTo(it) }
            }
        }
        assertTrue(backup.checkBackup(uri(plainDb)) is BackupCheck.Ready)
        photos.deleteRecursively()
    }
}
