package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.EntryKind
import com.teraper.printmaster.core.model.ImportedEntryDetail
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.testing.FakeImportRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.clients.entry.EntryDetailEvent
import com.teraper.printmaster.feature.clients.entry.EntryDetailUiState
import com.teraper.printmaster.feature.clients.entry.EntryDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val imports = FakeImportRepository().apply {
        details.value = mapOf(
            5L to ImportedEntryDetail(
                EntryKind.BANK_PAYMENT, 5, LocalDate.of(2025, 3, 5), Money.ofDram(6_000), clientId = 1, clientName = "Busy",
                documentNumber = "11", fileName = "bank.xlsx", importedAt = null, fields = listOf("Նպատակ" to "քարթրիջ"),
            ),
        )
    }
    private val clients = FakeClientsRepository(listOf(summary(1, "Busy"), summary(2, "Free")))

    private val payments = FakePaymentsRepository()

    private fun TestScope.vm(isCharge: Boolean) =
        EntryDetailViewModel(SavedStateHandle(mapOf("id" to 5L, "isCharge" to isCharge)), imports, payments, clients).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    @Test
    fun paymentCanMoveOrBeDetached() = runTest {
        val vm = vm(isCharge = false)
        val loaded = vm.uiState.value as EntryDetailUiState.Loaded
        assertEquals("քարթրիջ", loaded.detail.fields.single().second)

        vm.onMoveClick()
        // The current client isn't offered.
        assertEquals(listOf(2L), (vm.uiState.value as EntryDetailUiState.Loaded).pickClients.map { it.client.id })
        vm.onClientPicked(2)
        assertEquals(listOf(5L to 2L), imports.assigned)

        vm.onDetachClick()
        assertTrue((vm.uiState.value as EntryDetailUiState.Loaded).confirmDetach)
        vm.onConfirmDetach()
        assertEquals(listOf(5L), imports.detached)
    }

    @Test
    fun invoiceMoves() = runTest {
        val vm = vm(isCharge = true)
        vm.onClientPicked(2)
        assertEquals(listOf(5L to 2L), imports.movedInvoices)
    }

    @Test
    fun cashPaymentIsDeletedFromItsPageAndImportedOnesAreNot() = runTest {
        imports.details.value = mapOf(
            5L to ImportedEntryDetail(
                EntryKind.CASH_PAYMENT, 5, LocalDate.of(2025, 3, 5), Money.ofDram(6_000), clientId = 1, clientName = "Busy",
                documentNumber = null, fileName = null, importedAt = null, fields = emptyList(), note = "A very long note about the visit",
            ),
        )
        val vm = vm(isCharge = false)
        assertTrue((vm.uiState.value as EntryDetailUiState.Loaded).detail.canDelete)
        vm.onDeleteClick()
        assertTrue((vm.uiState.value as EntryDetailUiState.Loaded).confirmDelete)
        vm.onConfirmDelete()
        assertEquals(EntryDetailEvent.Deleted, vm.events.first())
        assertEquals(listOf(5L), payments.deleted.map { it.id })

        imports.details.value = mapOf(5L to imports.details.value.getValue(5L).copy(kind = EntryKind.BANK_PAYMENT))
        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(1, payments.deleted.size)
    }
}
