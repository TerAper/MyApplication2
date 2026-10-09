package com.teraper.printmaster.feature.calls

import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.core.testing.FakeCallRecordingsRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CallsViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val time = LocalDateTime.of(2026, 10, 9, 18, 0)
    private val repo = FakeCallRecordingsRepository(
        listOf(
            CallRecording(1, "u1", "a.m4a", "077001020", time, 1_000),
            CallRecording(2, "u2", "b.m4a", "Apo", time, 1_000, listOf(RecordingClient(5, "Apo"))),
        ),
    )
    private val clients = object : ClientsRepository {
        val list = MutableStateFlow(
            listOf("Gamma LLC", "Apo").mapIndexed { i, name ->
                ClientSummary(Client(i + 5L, name, ClientType.FIRM, null, "", emptyList(), emptyList()))
            },
        )
        override fun observeClientSummaries() = list
        override fun observeClientSummary(id: Long) = list.map { l -> l.firstOrNull { it.client.id == id } }
        override suspend fun saveClient(draft: ClientDraft) = SaveClientResult.Saved(0)
        override suspend fun deleteClient(id: Long) = DeleteClientResult.DELETED
    }

    private fun kotlinx.coroutines.test.TestScope.vm() = CallsViewModel(repo, clients).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
    }

    @Test
    fun scansOnOpenAndShowsUnmatchedFirst() = runTest {
        val vm = vm()
        assertEquals(1, repo.scans)
        assertEquals(listOf(1L), vm.uiState.value.recordings.map { it.id })
        vm.onTabChange(CallsTab.ALL)
        assertEquals(2, vm.uiState.value.recordings.size)
    }

    @Test
    fun attachToPickedClientAndIgnore() = runTest {
        val vm = vm()
        vm.onAttachClick(vm.uiState.value.recordings.single())
        vm.onPickerQueryChange("gamma")
        assertEquals(listOf("Gamma LLC"), vm.uiState.value.pickerClients.map { it.client.name })
        vm.onClientPicked(5)
        assertNull(vm.uiState.value.picker)
        assertTrue(vm.uiState.value.recordings.isEmpty())

        vm.onTabChange(CallsTab.ALL)
        vm.onIgnore(vm.uiState.value.recordings.first { it.id == 2L })
        assertTrue(repo.recordings.value.first { it.id == 2L }.ignored)
    }

    @Test
    fun pickingAnUnreadableFolderShowsAnError() = runTest {
        repo.folderReadable = false
        val vm = vm()
        vm.onFolderPicked("content://tree")
        assertTrue(vm.uiState.value.folderError)

        repo.folderReadable = true
        vm.onFolderPicked("content://tree")
        assertEquals("content://tree", vm.uiState.value.status.folderName)
        assertEquals(2, repo.scans)
    }
}
