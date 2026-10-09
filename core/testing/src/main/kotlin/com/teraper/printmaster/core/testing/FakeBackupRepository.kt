package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.BackupRepository
import com.teraper.printmaster.core.model.BackupCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Records calls; [checkResult] is what any picked file turns out to be. */
class FakeBackupRepository(
    var checkResult: BackupCheck = BackupCheck.NotABackup,
    var saveSucceeds: Boolean = true,
    private val now: Instant = Instant.parse("2026-10-09T10:00:00Z"),
) : BackupRepository {
    val lastBackup = MutableStateFlow<Instant?>(null)
    val savedTo = mutableListOf<String>()
    var checked: String? = null
    var restored = false
    var discarded = false

    override fun observeLastBackup(): Flow<Instant?> = lastBackup

    override fun suggestedFileName() = "PrintMaster-2026-10-09.db"

    override suspend fun saveBackup(uri: String): Boolean {
        if (!saveSucceeds) return false
        savedTo += uri
        lastBackup.value = now
        return true
    }

    override suspend fun checkBackup(uri: String): BackupCheck {
        checked = uri
        return checkResult
    }

    override suspend fun restoreCheckedBackup(): Boolean {
        restored = checked != null
        return restored
    }

    override suspend fun discardCheckedBackup() {
        discarded = true
        checked = null
    }
}
