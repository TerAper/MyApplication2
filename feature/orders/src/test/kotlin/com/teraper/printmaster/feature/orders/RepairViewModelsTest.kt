package com.teraper.printmaster.feature.orders

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemPart
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.Repair
import com.teraper.printmaster.core.model.RepairDraftError
import com.teraper.printmaster.core.model.RepairLine
import com.teraper.printmaster.core.testing.FakeCallRecordingsRepository
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeOrdersRepository
import com.teraper.printmaster.core.testing.FakePhotoRepository
import com.teraper.printmaster.core.testing.FakePriceListRepository
import com.teraper.printmaster.core.testing.FakeCatalogRepository
import com.teraper.printmaster.core.testing.FakeClientItemsRepository
import com.teraper.printmaster.core.testing.FakeRepairsRepository
import com.teraper.printmaster.core.testing.FakeWarehouseRepository
import com.teraper.printmaster.core.testing.FakeTrackingRepository
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.orders.detail.OrderDetailDialog
import com.teraper.printmaster.feature.orders.detail.OrderDetailUiState
import com.teraper.printmaster.feature.orders.detail.OrderDetailViewModel
import com.teraper.printmaster.feature.orders.repair.RepairEditDialog
import com.teraper.printmaster.feature.orders.repair.RepairEditEvent
import com.teraper.printmaster.feature.orders.repair.RepairEditViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepairViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
    private val orders = FakeOrdersRepository(listOf(Order(1, 1, 7, "Firm", LocalDateTime.of(2026, 10, 8, 10, 0), "Refill")))
    private val printerType = ItemType(1, "Printer")
    private val cartridgeType = ItemType(2, "Cartridge", parentId = 1)
    private val printer = ClientItem(5, 7, CatalogItem(1, printerType, "HP M125"), "Office", parts = listOf(ClientItemPart(50, CatalogItem(3, cartridgeType, "CF283A"))))
    private val refill = PriceItem(1, 2, "Refill", price = Money.ofDram(3_000), cost = Money.ofDram(900))
    private val drum = PriceItem(2, 1, "Drum", price = Money.ofDram(8_000))
    private val catalog = FakeCatalogRepository(listOf(printerType, cartridgeType))
    private val priceList = FakePriceListRepository(listOf(refill, drum))
    private val repairs = FakeRepairsRepository()
    private val warehouse = FakeWarehouseRepository()
    private val tracking = FakeTrackingRepository()

    private fun editVm(items: List<ClientItem> = listOf(printer), repairId: Long = 0) = RepairEditViewModel(
        SavedStateHandle(mapOf("orderId" to 1L, "repairId" to repairId)),
        orders, FakeClientItemsRepository(items), catalog, priceList, repairs, warehouse,
    )

    @Test
    fun newWorkPicksTheOnlyItemAndPickerFollowsTheDevice() = runTest {
        val vm = editVm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertEquals(5L, vm.uiState.value.form.draft.itemId)
        assertEquals(listOf(printerType, cartridgeType), vm.uiState.value.pickerTypes)
        assertFalse(vm.uiState.value.form.hasChanges)

        vm.onDeviceClick(5, 50)
        vm.onOpenPicker()
        assertEquals(listOf(refill), vm.uiState.value.pickerItems)

        vm.onPickItem(refill)
        vm.onPickItem(refill)
        vm.onPickerGroupClick(cartridgeType.id)
        assertEquals(listOf(refill, drum), vm.uiState.value.pickerItems)
        vm.onClosePicker()
        assertEquals(Money.ofDram(6_000), vm.uiState.value.form.draft.total)

        vm.onLinePriceChange(0, "2 500")
        assertEquals(Money.ofDram(5_000), vm.uiState.value.form.draft.total)

        vm.onSave()
        assertEquals(RepairEditEvent.Close, vm.events.first())
        val saved = repairs.saved.single()
        assertEquals(50L, saved.itemPartId)
        assertEquals(2, saved.lines.single().quantity)
    }

    @Test
    fun customLineNeedsNameAndPriceAndEmptyWorkIsRejected() = runTest {
        val vm = editVm(items = emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.form.draft.itemId)

        vm.onSave()
        assertEquals(setOf(RepairDraftError.LINES_REQUIRED), vm.uiState.value.form.errors)

        vm.onAddCustomClick()
        assertEquals(RepairEditDialog.CustomLine, vm.uiState.value.form.dialog)
        vm.onAddCustomLine("", "500", "")
        assertTrue(vm.uiState.value.form.draft.lines.isEmpty())
        vm.onAddCustomLine("Cleaning", "1500", "200")
        assertNull(vm.uiState.value.form.dialog)
        assertEquals(RepairLine(null, "Cleaning", Money.ofDram(1_500), Money.ofDram(200)), vm.uiState.value.form.draft.lines.single())
        assertTrue(vm.uiState.value.form.errors.isEmpty())
    }

    @Test
    fun savingFinishedOrderShowsLockedAndCloses() = runTest {
        repairs.repairs.value = listOf(Repair(3, 1, lines = listOf(RepairLine(1, "Refill", Money.ofDram(3_000)))))
        repairs.billed.value = mapOf(1L to Money.ZERO)
        val vm = editVm(repairId = 3)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertEquals("Refill", vm.uiState.value.form.draft.lines.single().name)

        vm.onQuantityChange(0, 2)
        vm.onSave()
        assertEquals(RepairEditDialog.Locked, vm.uiState.value.form.dialog)
        vm.onDismissDialog()
        assertEquals(RepairEditEvent.Close, vm.events.first())
    }

    @Test
    fun detailFinishesWithWorkAndAsksBeforeReopening() = runTest {
        repairs.repairs.value = listOf(Repair(3, 1, lines = listOf(RepairLine(1, "Refill", Money.ofDram(3_000), quantity = 2))))
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), orders, repairs, FakeCompaniesRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), warehouse, tracking, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        val loaded = { vm.uiState.value as OrderDetailUiState.Loaded }
        assertTrue(loaded().canFinishWithWork)
        assertEquals(Money.ofDram(6_000), loaded().work.total)

        vm.onFinish(paidInCash = true)
        assertEquals(listOf(1L to true), repairs.finished)
        assertEquals(Money.ofDram(6_000), loaded().work.paidCash)
        orders.setStatus(1, OrderStatus.DONE)
        assertFalse(loaded().canEditWork)

        vm.onStatusChange(OrderStatus.NEW)
        assertEquals(OrderDetailDialog.CONFIRM_REOPEN, loaded().dialog)
        vm.onConfirmReopen()
        assertEquals(listOf(1L), repairs.reopened)
        assertFalse(loaded().work.isBilled)
    }

    @Test
    fun goodsThatFitTheChosenPartComeFirstAndAreAddedAsGoods() = runTest {
        val fitting = CatalogItem(3, cartridgeType, "CF283A", price = Money.ofDram(12_000), cost = Money.ofDram(7_000))
        val other = CatalogItem(4, cartridgeType, "CE285A", price = Money.ofDram(9_000))
        val notSold = CatalogItem(5, cartridgeType, "Q2612A")
        catalog.entries.value = listOf(CatalogEntry(other), CatalogEntry(fitting), CatalogEntry(notSold))
        val vm = editVm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.goodsAvailable)

        vm.onDeviceClick(5, 50)
        vm.onOpenPicker()
        vm.onPickerGoods(true)
        assertEquals(listOf("CF283A", "CE285A"), vm.uiState.value.pickerGoods.map { it.name })
        assertEquals(setOf(3L), vm.uiState.value.fittingGoods)

        vm.onPickGoods(fitting)
        val line = vm.uiState.value.form.draft.lines.single()
        assertEquals(3L, line.itemId)
        assertEquals(Money.ofDram(12_000), line.price)
        assertEquals(Money.ofDram(7_000), line.cost)
        // None in the warehouse: to buy first, unless unmarked.
        assertTrue(line.toBuy)
        vm.onToBuyChange(0, false)
        assertFalse(vm.uiState.value.form.draft.lines.single().toBuy)
    }

    @Test
    fun goodsInStockAreNotToBuy() = runTest {
        val toner = CatalogItem(3, cartridgeType, "CF283A", price = Money.ofDram(12_000))
        catalog.entries.value = listOf(CatalogEntry(toner))
        warehouse.lines.value = listOf(StockLine(toner, 1))
        val vm = editVm()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertEquals(mapOf(3L to 1), vm.uiState.value.goodsStock)
        vm.onPickGoods(toner)
        assertFalse(vm.uiState.value.form.draft.lines.single().toBuy)
        vm.onPickGoods(toner)
        assertTrue(vm.uiState.value.form.draft.lines.single().toBuy)
    }

    @Test
    fun detailListsMissingGoodsAndBoughtClearsThem() = runTest {
        val toner = CatalogItem(3, cartridgeType, "CF283A", price = Money.ofDram(12_000))
        warehouse.lines.value = listOf(StockLine(toner, 0))
        repairs.repairs.value = listOf(Repair(3, 1, lines = listOf(RepairLine(null, "CF283A", Money.ofDram(12_000), itemId = 3, toBuy = true))))
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), orders, repairs, FakeCompaniesRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), warehouse, tracking, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        val loaded = { vm.uiState.value as OrderDetailUiState.Loaded }
        assertEquals(listOf(3L to 1), loaded().goodsShortages.map { it.itemId to it.missing })

        vm.onBought(3)
        assertEquals(listOf(1L to 3L), repairs.bought)
        // The fake doesn't stock it, so it's still short of the warehouse; once there, nothing is missing.
        warehouse.lines.value = listOf(StockLine(toner, 1))
        assertTrue(loaded().goodsShortages.isEmpty())
    }

    @Test
    fun companySeesTheWayOnlyWhileTheMasterShares() = runTest {
        val asked = Order(1, 1, 7, "Firm", LocalDateTime.of(2026, 10, 8, 10, 0), "Refill", masterName = "Armen", masterAttached = true, masterId = 3, askLocation = true)
        val orders = FakeOrdersRepository(listOf(asked))
        tracking.ways.value = mapOf(1L to listOf(com.teraper.printmaster.core.model.MapPoint(40.18, 44.51, 1_000)))
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), orders, repairs, FakeCompaniesRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), warehouse, tracking, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        val loaded = { vm.uiState.value as OrderDetailUiState.Loaded }
        // Asked, no answer yet: nothing to show on the map.
        assertTrue(loaded().followsMaster)
        assertTrue(loaded().way.isEmpty())

        orders.orders.value = listOf(asked.copy(acceptedAt = LocalDateTime.of(2026, 10, 8, 9, 0), sharingLocation = true))
        assertEquals(1, loaded().way.size)
    }

    @Test
    fun masterSharesAndStopsOnTheOrder() = runTest {
        val given = Order(
            1, 1, 7, "Firm", LocalDateTime.of(2026, 10, 8, 10, 0), "Refill",
            fromAttachedCompany = true, companyName = "Xerox", askLocation = true, acceptedAt = LocalDateTime.of(2026, 10, 8, 9, 0),
        )
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), FakeOrdersRepository(listOf(given)), repairs, FakeCompaniesRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), warehouse, tracking, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertTrue((vm.uiState.value as OrderDetailUiState.Loaded).canShareLocation)
        vm.onShareLocation(true)
        assertEquals(setOf(1L), tracking.sharing.value)
        vm.onShareLocation(false)
        assertTrue(tracking.sharing.value.isEmpty())
    }

    @Test
    fun acceptingWithSharingTellsTheRepository() = runTest {
        val asked = Order(1, 1, 7, "Firm", LocalDateTime.of(2026, 10, 8, 10, 0), "Refill", fromAttachedCompany = true, askLocation = true)
        val vm = OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 1L)), FakeOrdersRepository(listOf(asked)), repairs, FakeCompaniesRepository(), FakeCallRecordingsRepository(), FakePhotoRepository(), warehouse, tracking, clock)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        vm.onAcceptSharing()
        assertEquals(mapOf(1L to null), repairs.accepted)
        assertEquals(setOf(1L), repairs.sharedLocation)
    }
}
