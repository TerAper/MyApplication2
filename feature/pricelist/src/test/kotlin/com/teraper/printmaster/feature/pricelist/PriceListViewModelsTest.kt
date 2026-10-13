package com.teraper.printmaster.feature.pricelist

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.testing.FakeCatalogRepository
import com.teraper.printmaster.core.testing.FakePriceListRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditDialog
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditEvent
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditViewModel
import com.teraper.printmaster.feature.pricelist.list.PriceListUiState
import com.teraper.printmaster.feature.pricelist.list.PriceListViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class PriceListViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val printer = ItemType(1, "Printer")
    private val cartridge = ItemType(2, "Cartridge", parentId = 1)
    private val catalog = FakeCatalogRepository(listOf(printer, cartridge))
    private val refill = PriceItem(1, 2, "Refill 85A", "HP", Money.ofDram(3_000), Money.ofDram(900))
    private val chip = PriceItem(2, 2, "Chip", price = Money.ofDram(2_500))
    private val drum = PriceItem(3, 1, "Drum", price = Money.ofDram(8_000))
    private val visit = PriceItem(4, null, "Visit", price = Money.ofDram(1_000))
    private val repo = FakePriceListRepository(listOf(refill, chip, drum, visit))

    private fun editVm(itemId: Long = 0, typeId: Long = 0) =
        PriceItemEditViewModel(SavedStateHandle(mapOf("itemId" to itemId, "typeId" to typeId)), repo, catalog)

    @Test
    fun listGroupsByTypeFiltersAndSearches() = runTest {
        val vm = PriceListViewModel(repo, catalog)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(4, vm.uiState.value.totalCount)
        // Types in their order, general work last.
        assertEquals(listOf("Printer" to 1, "Cartridge" to 2, null to 1), vm.uiState.value.chips.map { it.first.type?.name to it.second })
        assertEquals(3, vm.uiState.value.groups.size)

        vm.onGroupClick(printer.id)
        assertEquals(listOf(drum), vm.uiState.value.groups.single().items)
        vm.onGroupClick(PriceListUiState.GENERAL)
        assertEquals(listOf(visit), vm.uiState.value.groups.single().items)
        // Tapping the selected chip again shows everything.
        vm.onGroupClick(PriceListUiState.GENERAL)
        assertEquals(null, vm.uiState.value.group)

        vm.onQueryChange("hp")
        assertEquals(listOf(refill), vm.uiState.value.groups.single().items)
    }

    @Test
    fun newItemStartsForTheGivenTypeAndSaves() = runTest {
        val vm = editVm(typeId = printer.id)
        assertEquals(printer.id, vm.uiState.value.draft.typeId)
        assertEquals(null, editVm().uiState.value.draft.typeId)

        vm.onSave()
        assertEquals(setOf(PriceItemError.NAME_REQUIRED, PriceItemError.PRICE_REQUIRED), vm.uiState.value.errors)

        vm.onNameChange("Roller")
        vm.onPriceChange("4 500")
        vm.onCostChange("1500")
        assertTrue(vm.uiState.value.errors.isEmpty())
        assertEquals(Money.ofDram(3_000), vm.uiState.value.draft.profit)

        vm.onSave()
        assertEquals(PriceItemEditEvent.Close, vm.events.first())
        assertEquals("Roller", repo.items.value.last().name)
    }

    @Test
    fun duplicateNameIsShownUntilChanged() = runTest {
        val vm = editVm(typeId = cartridge.id)
        vm.onNameChange("chip")
        vm.onPriceChange("100")
        vm.onSave()
        assertTrue(vm.uiState.value.nameTaken)

        vm.onTypeChange(null)
        assertFalse(vm.uiState.value.nameTaken)
    }

    @Test
    fun editLoadsItemAndAsksBeforeDiscarding() = runTest {
        val vm = editVm(itemId = 1)
        assertEquals("3000", vm.uiState.value.draft.priceDigits)
        assertEquals("900", vm.uiState.value.draft.costDigits)

        vm.onPriceChange("3200")
        vm.onCloseRequest()
        assertEquals(PriceItemEditDialog.DISCARD, vm.uiState.value.dialog)

        vm.onDismissDialog()
        vm.onDeleteClick()
        vm.onConfirmDelete()
        assertEquals(PriceItemEditEvent.Close, vm.events.first())
        assertFalse(repo.items.value.any { it.id == 1L })
    }

    @Test
    fun theGoodsSideListsCatalogItemsWithAPrice() = runTest {
        catalog.entries.value = listOf(
            com.teraper.printmaster.core.model.CatalogEntry(com.teraper.printmaster.core.model.CatalogItem(1, cartridge, "CF283A", price = Money.ofDram(12_000))),
            com.teraper.printmaster.core.model.CatalogEntry(com.teraper.printmaster.core.model.CatalogItem(2, cartridge, "Not sold")),
        )
        val vm = PriceListViewModel(repo, catalog)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        vm.onShowGoods(true)
        assertTrue(vm.uiState.value.showGoods)
        assertEquals(listOf("CF283A"), vm.uiState.value.goods.map { it.name })
        assertEquals(1, vm.uiState.value.goodsCount)
    }
}
