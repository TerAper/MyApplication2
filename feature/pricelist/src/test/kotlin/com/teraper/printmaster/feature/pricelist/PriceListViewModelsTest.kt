package com.teraper.printmaster.feature.pricelist

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemError
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.core.testing.FakePriceListRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditDialog
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditEvent
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditViewModel
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

    private val refill = PriceItem(1, RepairCategory.CARTRIDGE, "Refill 85A", "HP", Money.ofDram(3_000), Money.ofDram(900))
    private val chip = PriceItem(2, RepairCategory.CARTRIDGE, "Chip", price = Money.ofDram(2_500))
    private val drum = PriceItem(3, RepairCategory.PRINTER, "Drum", price = Money.ofDram(8_000))
    private val repo = FakePriceListRepository(listOf(refill, chip, drum))

    private fun editVm(itemId: Long = 0, category: String? = null) =
        PriceItemEditViewModel(SavedStateHandle(mapOf("itemId" to itemId, "category" to category)), repo)

    @Test
    fun listFiltersByCategoryAndSearch() = runTest {
        val vm = PriceListViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(3, vm.uiState.value.totalCount)
        assertEquals(mapOf(RepairCategory.CARTRIDGE to 2, RepairCategory.PRINTER to 1), vm.uiState.value.counts)
        assertEquals(2, vm.uiState.value.groups.size)

        vm.onCategoryClick(RepairCategory.PRINTER)
        assertEquals(listOf(drum), vm.uiState.value.groups.single().items)
        // Tapping the selected chip again shows everything.
        vm.onCategoryClick(RepairCategory.PRINTER)
        assertEquals(null, vm.uiState.value.category)

        vm.onQueryChange("hp")
        assertEquals(listOf(refill), vm.uiState.value.groups.single().items)
    }

    @Test
    fun newItemStartsInGivenCategoryAndSaves() = runTest {
        val vm = editVm(category = "PRINTER")
        assertEquals(RepairCategory.PRINTER, vm.uiState.value.draft.category)

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
        val vm = editVm()
        vm.onNameChange("chip")
        vm.onPriceChange("100")
        vm.onSave()
        assertTrue(vm.uiState.value.nameTaken)

        vm.onCategoryChange(RepairCategory.OTHER)
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
}
