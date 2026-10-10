package com.teraper.printmaster.feature.orders

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.data.repository.ClientsRepository
import com.teraper.printmaster.core.data.repository.DeleteClientResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.model.CallRecording
import com.teraper.printmaster.core.model.Client
import com.teraper.printmaster.core.model.ClientAddress
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientPhone
import com.teraper.printmaster.core.model.ClientSummary
import com.teraper.printmaster.core.model.ClientType
import com.teraper.printmaster.core.model.Master
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.RecordingClient
import com.teraper.printmaster.core.testing.FakeCallRecordingsRepository
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeOrdersRepository
import com.teraper.printmaster.core.testing.FakePhotoRepository
import com.teraper.printmaster.core.testing.FakeRepairsRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.orders.detail.OrderDetailDialog
import com.teraper.printmaster.feature.orders.detail.OrderDetailUiState
import com.teraper.printmaster.feature.orders.detail.OrderDetailViewModel
import com.teraper.printmaster.feature.orders.edit.OrderEditEvent
import com.teraper.printmaster.feature.orders.edit.OrderEditViewModel
import com.teraper.printmaster.feature.orders.list.OrdersViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 8)

    private val firm = Client(
        1, "Firm", ClientType.FIRM, null, "",
        phones = listOf(ClientPhone(11, "091", "")),
        addresses = listOf(ClientAddress(21, "Komitas 5", ""), ClientAddress(22, "Store", "")),
    )

    private val clients = object : ClientsRepository {
        val list = MutableStateFlow(listOf(ClientSummary(firm), ClientSummary(Client(2, "Other", ClientType.PRIVATE, null, "", emptyList(), emptyList()))))
        override fun observeClientSummaries(): Flow<List<ClientSummary>> = list
        override fun observeClientSummary(id: Long) = list.map { l -> l.firstOrNull { it.client.id == id } }
        override suspend fun saveClient(draft: ClientDraft) = SaveClientResult.Saved(0)
        override suspend fun deleteClient(id: Long) = DeleteClientResult.DELETED
    }

    private fun order(id: Long, date: LocalDate, hour: Int, status: OrderStatus = OrderStatus.NEW) =
        Order(id, 1, 1, "Firm", date.atTime(LocalTime.of(hour, 0)), "Job $id", status)

    private fun TestScope.collect(flow: StateFlow<*>) = backgroundScope.launch(UnconfinedTestDispatcher()) { flow.collect {} }

    private fun editVm(orders: FakeOrdersRepository, companies: FakeCompaniesRepository = FakeCompaniesRepository(), args: Map<String, Any> = emptyMap()) =
        OrderEditViewModel(SavedStateHandle(args), clients, companies, orders, clock)

    @Test
    fun dayListShowsSelectedDayAndOverdueOnlyOnToday() = runTest {
        val orders = FakeOrdersRepository(
            listOf(order(1, today, 14), order(2, today, 9), order(3, today.minusDays(2), 10), order(4, today.plusDays(1), 10)),
        )
        val vm = OrdersViewModel(orders, clock)
        collect(vm.uiState)

        assertEquals(listOf(2L, 1L), vm.uiState.value.orders.map { it.id })
        assertEquals(listOf(3L), vm.uiState.value.overdue.map { it.id })
        assertEquals(2, vm.uiState.value.counts[today])

        vm.onDateSelected(today.plusDays(1))
        assertEquals(listOf(4L), vm.uiState.value.orders.map { it.id })
        assertTrue(vm.uiState.value.overdue.isEmpty())
    }

    @Test
    fun newOrderWithoutClientOpensPickerAndPickingFillsContacts() = runTest {
        val orders = FakeOrdersRepository()
        val vm = editVm(orders)
        collect(vm.uiState); collect(vm.pickerClients)

        assertTrue(vm.uiState.value.form.showClientPicker)
        vm.onClientPicked(1)
        val draft = vm.uiState.value.draft
        assertEquals(21L, draft.addressId)
        assertEquals(11L, draft.phoneId)
        assertEquals(today, draft.date)
        assertFalse(vm.uiState.value.form.showClientPicker)
    }

    @Test
    fun presetClientAndDateAndSingleMasterAreUsed() = runTest {
        val orders = FakeOrdersRepository()
        val companies = FakeCompaniesRepository().apply { masters.value = listOf(Master(5, "Aram")) }
        val vm = editVm(orders, companies, mapOf("clientId" to 1L, "dateEpochDay" to today.plusDays(1).toEpochDay()))
        collect(vm.uiState)

        val draft = vm.uiState.value.draft
        assertEquals(1L, draft.clientId)
        assertEquals(today.plusDays(1), draft.date)
        assertEquals(5L, draft.masterId)
        assertFalse(vm.uiState.value.form.showClientPicker)
    }

    @Test
    fun saveNeedsDescriptionThenCreates() = runTest {
        val orders = FakeOrdersRepository()
        val vm = editVm(orders, args = mapOf("clientId" to 1L))
        collect(vm.uiState)

        vm.onSave()
        assertEquals(setOf(OrderDraftError.DESCRIPTION_REQUIRED), vm.uiState.value.form.errors)

        vm.onDescriptionChange("Refill 2 cartridges")
        vm.onTimeChange(LocalTime.of(15, 0))
        vm.onAddressChange(22)
        vm.onSave()
        assertEquals(OrderEditEvent.Saved(1, wasNew = true), vm.events.first())
        val saved = orders.saved.single()
        assertEquals(LocalTime.of(15, 0), saved.time)
        assertEquals(22L, saved.addressId)
    }

    @Test
    fun detailChangesStatusAsksBeforeCancellingAndBlocksDelete() = runTest {
        val orders = FakeOrdersRepository(listOf(order(1, today, 10)))
        orders.blockedIds = setOf(1)
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), orders, FakeRepairsRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), clock)
        collect(vm.uiState)
        val loaded = { vm.uiState.value as OrderDetailUiState.Loaded }

        vm.onStatusChange(OrderStatus.IN_PROGRESS)
        assertEquals(OrderStatus.IN_PROGRESS, loaded().order.status)

        vm.onStatusChange(OrderStatus.CANCELLED)
        assertEquals(OrderDetailDialog.CONFIRM_CANCEL, loaded().dialog)
        assertEquals(OrderStatus.IN_PROGRESS, loaded().order.status)
        vm.onConfirmCancel()
        assertEquals(OrderStatus.CANCELLED, loaded().order.status)
        assertNull(loaded().dialog)

        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(OrderDetailDialog.DELETE_BLOCKED, loaded().dialog)
    }

    @Test
    fun detailShowsTheClientsCallsOfThatDay() = runTest {
        val orders = FakeOrdersRepository(listOf(order(1, today, 10)))
        fun call(id: Long, day: LocalDate, clientId: Long) =
            CallRecording(id, "u$id", "f$id.m4a", "Firm", day.atTime(9, 0), 1_000, listOf(RecordingClient(clientId, "Firm")))
        val calls = FakeCallRecordingsRepository(listOf(call(1, today, 1), call(2, today.minusDays(1), 1), call(3, today, 2)))
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), orders, FakeRepairsRepository(), calls, FakePhotoRepository(), clock)
        collect(vm.uiState)

        assertEquals(listOf(1L), (vm.uiState.value as OrderDetailUiState.Loaded).calls.map { it.id })
    }
}
