package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.feature.clients.detail.ClientDetailDialog
import com.teraper.printmaster.feature.clients.detail.ClientDetailEvent
import com.teraper.printmaster.feature.clients.detail.ClientDetailUiState
import com.teraper.printmaster.feature.clients.detail.ClientDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeClientsRepository(listOf(summary(1, "Busy"), summary(2, "Free")))

    private fun vm(id: Long) = ClientDetailViewModel(SavedStateHandle(mapOf("clientId" to id)), repo)

    @Test
    fun deleteAsksThenDeletes() = runTest {
        val vm = vm(2)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onDeleteClick()
        assertEquals(ClientDetailDialog.CONFIRM_DELETE, (vm.uiState.value as ClientDetailUiState.Loaded).dialog)

        vm.onConfirmDelete()
        assertEquals(ClientDetailEvent.Deleted, vm.events.first())
        assertEquals(listOf("Busy"), repo.clients.value.map { it.client.name })
    }

    @Test
    fun clientWithRecordsShowsBlockedMessage() = runTest {
        repo.blockedIds = setOf(1)
        val vm = vm(1)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(ClientDetailDialog.DELETE_BLOCKED, (vm.uiState.value as ClientDetailUiState.Loaded).dialog)
        assertEquals(2, repo.clients.value.size)
    }

    @Test
    fun unknownClientIsNotFound() = runTest {
        val vm = vm(42)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertEquals(ClientDetailUiState.NotFound, vm.uiState.value)
    }
}
