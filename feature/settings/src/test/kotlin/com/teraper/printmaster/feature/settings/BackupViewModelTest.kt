package com.teraper.printmaster.feature.settings

import com.teraper.printmaster.core.model.BackupCheck
import com.teraper.printmaster.core.model.BackupSummary
import com.teraper.printmaster.core.testing.FakeBackupRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.settings.backup.BackupDialog
import com.teraper.printmaster.feature.settings.backup.BackupEvent
import com.teraper.printmaster.feature.settings.backup.BackupViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeBackupRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)

    private fun vm() = BackupViewModel(repo, clock)

    @Test
    fun savingUpdatesLastBackup() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.needsBackup)

        vm.onSaveTo(null)
        assertTrue(repo.savedTo.isEmpty())

        vm.onSaveTo("content://drive/backup")
        assertEquals(listOf("content://drive/backup"), repo.savedTo)
        assertEquals(BackupDialog.Saved, vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.needsBackup)

        // A week later it asks again.
        repo.lastBackup.value = Instant.parse("2026-10-01T10:00:00Z")
        assertTrue(vm.uiState.value.needsBackup)

        repo.saveSucceeds = false
        vm.onDismissDialog()
        vm.onSaveTo("content://x")
        assertEquals(BackupDialog.SaveFailed, vm.uiState.value.dialog)
    }

    @Test
    fun restoreAsksFirstThenRestarts() = runTest {
        val backup = BackupSummary(listOf("Alfa"), clients = 3)
        repo.checkResult = BackupCheck.Ready(backup, BackupSummary())
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onRestoreFrom("content://file")
        assertEquals(BackupDialog.ConfirmRestore(backup, BackupSummary()), vm.uiState.value.dialog)
        assertFalse(repo.restored)

        vm.onConfirmRestore()
        assertTrue(repo.restored)
        assertEquals(BackupEvent.RestartApp, vm.events.first())
    }

    @Test
    fun cancellingRestoreForgetsTheFile() = runTest {
        repo.checkResult = BackupCheck.Ready(BackupSummary(), BackupSummary())
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onRestoreFrom("content://file")
        vm.onDismissDialog()
        assertTrue(repo.discarded)
        assertFalse(repo.restored)

        repo.checkResult = BackupCheck.FromNewerApp
        vm.onRestoreFrom("content://newer")
        assertEquals(BackupDialog.FromNewerApp, vm.uiState.value.dialog)
    }
}
