package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.feature.clients.detail.ClientDetailDialog
import com.teraper.printmaster.feature.clients.detail.ClientDetailEvent
import com.teraper.printmaster.feature.clients.detail.ClientDetailUiState
import com.teraper.printmaster.feature.clients.detail.ClientDetailViewModel
import com.teraper.printmaster.feature.clients.detail.ClientTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.core.testing.FakeCallRecordingsRepository
import java.time.LocalDateTime
import com.teraper.printmaster.core.testing.FakeOrdersRepository
import com.teraper.printmaster.core.testing.FakePrintersRepository
import java.time.Clock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ClientDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeClientsRepository(listOf(summary(1, "Busy"), summary(2, "Free")))
    private val payments = FakePaymentsRepository()
    private val day = LocalDate.of(2026, 10, 7)
    private val cash = LedgerEntry.Payment(10, day, Money.ofDram(5_000), "", 1, PaymentMethod.CASH, null)
    private val bank = LedgerEntry.Payment(11, day, Money.ofDram(9_000), "", 2, PaymentMethod.BANK, "77")
    private val invoice = LedgerEntry.Charge(12, day, Money.ofDram(20_000), "", 3, ChargeSource.INVOICE_IMPORT, "0451")

    private fun TestScope.vm(id: Long) = ClientDetailViewModel(
        SavedStateHandle(mapOf("clientId" to id)), repo, payments, FakePrintersRepository(), FakeOrdersRepository(), calls, Clock.systemUTC(),
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
    }

    private val calls = FakeCallRecordingsRepository(
        listOf(
            CallRecording(1, "uri1", "a.m4a", "Firm", LocalDateTime.of(2026, 10, 9, 10, 0), 60_000, listOf(RecordingClient(2, "Firm"))),
            CallRecording(2, "uri2", "b.m4a", "077001020", LocalDateTime.of(2026, 10, 9, 11, 0), 5_000),
        ),
    )

    private val ClientDetailViewModel.loaded get() = uiState.value as ClientDetailUiState.Loaded

    @Test
    fun showsTheClientsCallsAndPlaysOne() = runTest {
        val vm = vm(2)
        assertEquals(listOf(1L), vm.loaded.calls.map { it.id })
        vm.onPlayCall(vm.loaded.calls.single())
        assertEquals(1L, vm.loaded.playing?.id)
        vm.onStopCall()
        assertEquals(null, vm.loaded.playing)
    }

    @Test
    fun deleteAsksThenDeletes() = runTest {
        val vm = vm(2)
        vm.onDeleteClick()
        assertEquals(ClientDetailDialog.ConfirmDelete, vm.loaded.dialog)

        vm.onConfirmDelete()
        assertEquals(ClientDetailEvent.Deleted, vm.events.first())
        assertEquals(listOf("Busy"), repo.clients.value.map { it.client.name })
    }

    @Test
    fun clientWithRecordsShowsBlockedMessage() = runTest {
        repo.blockedIds = setOf(1)
        val vm = vm(1)
        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(ClientDetailDialog.DeleteBlocked, vm.loaded.dialog)
        assertEquals(2, repo.clients.value.size)
    }

    @Test
    fun unknownClientIsNotFound() = runTest {
        assertEquals(ClientDetailUiState.NotFound, vm(42).uiState.value)
    }

    @Test
    fun financeTabShowsLedger() = runTest {
        payments.ledgers.value = mapOf(1L to listOf(cash, bank, invoice))
        val vm = vm(1)
        vm.onTabSelected(ClientTab.FINANCE)
        assertEquals(ClientTab.FINANCE, vm.loaded.tab)
        assertEquals(listOf(cash, bank, invoice), vm.loaded.ledger)
    }
}
