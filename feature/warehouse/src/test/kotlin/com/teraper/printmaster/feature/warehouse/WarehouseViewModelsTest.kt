package com.teraper.printmaster.feature.warehouse

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.AdjustReason
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockDraftError
import com.teraper.printmaster.core.model.StockFilter
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.core.testing.FakeCatalogRepository
import com.teraper.printmaster.core.testing.FakeCompaniesRepository
import com.teraper.printmaster.core.testing.FakeWarehouseRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.warehouse.item.ItemStockViewModel
import com.teraper.printmaster.feature.warehouse.list.WarehouseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first
import com.teraper.printmaster.feature.warehouse.list.ScannedIn
import com.teraper.printmaster.feature.warehouse.list.WarehouseEvent

@OptIn(ExperimentalCoroutinesApi::class)
class WarehouseViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val cartridge = ItemType(1, "Cartridge")
    private val toner = CatalogItem(1, cartridge, "Toner", cost = Money.ofDram(1_000))
    private val chip = CatalogItem(2, cartridge, "Chip", cost = Money.ofDram(500), keepAtLeast = 5)
    private val drum = CatalogItem(3, cartridge, "Drum", cost = Money.ofDram(4_000))
    private val catalog = com.teraper.printmaster.core.testing.FakeCatalogRepository()
    private val warehouse = FakeWarehouseRepository(listOf(StockLine(toner, 3), StockLine(chip, 2, 5), StockLine(drum, 0)))

    @Test
    fun masterSeesTheStockAnAttachedCompanyShares() = runTest {
        val companies = FakeCompaniesRepository()
        companies.attached.value = listOf(Company(7, "Xerox"), Company(8, "Quiet"))
        warehouse.companyStock.value = listOf(
            com.teraper.printmaster.core.model.CompanyStockLine(7, "Cartridge", "CF283A", 4, Money.ofDram(9_000)),
            com.teraper.printmaster.core.model.CompanyStockLine(7, "Drum", "DR-1", 0),
        )
        val vm = WarehouseViewModel(warehouse, companies, catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        // Only companies that share something get a chip; the own warehouse shows first.
        assertEquals(listOf("Xerox"), vm.uiState.value.companies.map { it.name })
        assertEquals(null, vm.uiState.value.companyId)

        vm.onCompanyChange(7)
        assertEquals(listOf("CF283A", "DR-1"), vm.uiState.value.companyLines.map { it.name })
        vm.onQueryChange("drum")
        assertEquals(listOf("DR-1"), vm.uiState.value.companyLines.map { it.name })
    }

    @Test
    fun listsLowFirstThenInStockAndSums() = runTest {
        val vm = WarehouseViewModel(warehouse, FakeCompaniesRepository(), catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        val state = vm.uiState.value
        assertEquals(listOf("Chip", "Toner", "Drum"), state.lines.map { it.item.name })
        assertEquals(2, state.inStock)
        assertEquals(1, state.lowCount)
        assertEquals(Money.ofDram(4_000), state.value)
        // One company: nothing to split.
        assertFalse(state.canSplit)

        vm.onFilterChange(StockFilter.OUT)
        assertEquals(listOf("Drum"), vm.uiState.value.lines.map { it.item.name })
        vm.onFilterChange(StockFilter.ALL)
        vm.onQueryChange("ton")
        assertEquals(listOf("Toner"), vm.uiState.value.lines.map { it.item.name })
    }

    @Test
    fun modeCanBeChosenWithTwoCompanies() = runTest {
        val vm = WarehouseViewModel(warehouse, FakeCompaniesRepository(listOf(Company(1, "Main"), Company(2, "Second"))), catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.canSplit)
        vm.onShowMode()
        vm.onModeChange(WarehouseMode.PER_COMPANY)
        assertEquals(WarehouseMode.PER_COMPANY, vm.uiState.value.mode)
        assertFalse(vm.uiState.value.showMode)
    }

    private fun itemStock(companies: FakeCompaniesRepository = FakeCompaniesRepository()) = ItemStockViewModel(
        SavedStateHandle(mapOf("itemId" to 2L)),
        FakeCatalogRepository(listOf(cartridge), listOf(CatalogEntry(toner), CatalogEntry(chip), CatalogEntry(drum))),
        companies,
        warehouse,
    )

    @Test
    fun receiveStartsFromLastCostAndAddsPieces() = runTest {
        val vm = itemStock()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value.isLow)
        vm.onReceive()
        assertEquals("500", vm.uiState.value.draft?.costDigits)
        vm.onSave()
        assertEquals(setOf(StockDraftError.QUANTITY_REQUIRED), vm.uiState.value.errors)
        vm.onQuantityChange("4")
        assertTrue(vm.uiState.value.errors.isEmpty())
        vm.onSave()
        assertNull(vm.uiState.value.draft)
        assertEquals(6, vm.uiState.value.count)
        assertFalse(vm.uiState.value.isLow)
        assertEquals(listOf(4), vm.uiState.value.moves.map { it.quantity })
    }

    @Test
    fun adjustTypesTheCountedNumber() = runTest {
        val vm = itemStock()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAdjust()
        assertEquals("2", vm.uiState.value.draft?.quantityDigits)
        vm.onSave()
        assertEquals(setOf(StockDraftError.NO_CHANGE), vm.uiState.value.errors)
        vm.onQuantityChange("1")
        vm.onReasonChange(AdjustReason.BROKEN)
        vm.onSave()
        assertEquals(1, vm.uiState.value.count)
        val move = vm.uiState.value.moves.single()
        assertEquals(StockKind.ADJUST to AdjustReason.BROKEN, move.kind to move.reason)
        assertEquals(-1, move.quantity)
    }

    @Test
    fun moveOnlyBetweenSeparateWarehouses() = runTest {
        val companies = FakeCompaniesRepository(listOf(Company(1, "Main"), Company(2, "Second")))
        val vm = itemStock(companies)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        // One shared warehouse: nowhere to move to.
        assertTrue(vm.uiState.value.moveTargets.isEmpty())
        warehouse.mode.value = WarehouseMode.PER_COMPANY
        assertEquals(listOf(2L), vm.uiState.value.moveTargets.map { it.id })
        vm.onMove()
        // The only other company is chosen already.
        assertEquals(2L, vm.uiState.value.draft?.toCompanyId)
        vm.onQuantityChange("3")
        vm.onSave()
        assertEquals(setOf(StockDraftError.NOT_ENOUGH), vm.uiState.value.errors)
    }

    @Test
    fun scanningAKnownCodeReceivesOneAndCanBeUndone() = runTest {
        val coded = toner.copy(barcode = "4960999123456")
        catalog.entries.value = listOf(com.teraper.printmaster.core.model.CatalogEntry(coded))
        val vm = WarehouseViewModel(warehouse, FakeCompaniesRepository(), catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onScanned("4960999123456")
        assertEquals(WarehouseEvent.ScanNext, vm.events.first())
        assertEquals(ScannedIn("Toner", 4, 1), vm.uiState.value.scanned)
        assertEquals(4, warehouse.lines.value.first { it.item.id == 1L }.count)

        vm.onUndoScan()
        assertEquals(3, warehouse.lines.value.first { it.item.id == 1L }.count)
        assertEquals(null, vm.uiState.value.scanned)
    }

    @Test
    fun anUnknownCodeOffersANewItem() = runTest {
        val vm = WarehouseViewModel(warehouse, FakeCompaniesRepository(), catalog)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onScanned(" 123 ")
        assertEquals("123", vm.uiState.value.newCode)
        vm.onAddNewCode()
        assertEquals(WarehouseEvent.NewItem("123"), vm.events.first())
        assertEquals(null, vm.uiState.value.newCode)
    }
}
