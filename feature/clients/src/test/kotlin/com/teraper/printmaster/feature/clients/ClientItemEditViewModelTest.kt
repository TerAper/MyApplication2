package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.ClientItemPart
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.SerialMatch
import com.teraper.printmaster.core.testing.FakeCatalogRepository
import com.teraper.printmaster.core.testing.FakeClientItemsRepository
import com.teraper.printmaster.feature.clients.item.ClientItemEditDialog
import com.teraper.printmaster.feature.clients.item.ClientItemEditEvent
import com.teraper.printmaster.feature.clients.item.ClientItemEditViewModel
import com.teraper.printmaster.feature.clients.item.ModelStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientItemEditViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val printer = ItemType(1, "Printer", fields = listOf(ItemField(1, 1, "Brand", inTitle = true)))
    private val cartridge = ItemType(2, "Cartridge", parentId = 1)
    private val toner = ItemType(3, "Toner", parentId = 1)
    private val shoes = ItemType(4, "Shoes")
    private val m125 = CatalogItem(
        1, printer, "LaserJet M125", mapOf(1L to "HP"),
        parts = listOf(ItemRef(5, 2, "Cartridge", "CF283A"), ItemRef(6, 2, "Cartridge", "CF283X")),
    )
    private val mf3010 = CatalogItem(2, printer, "i-SENSYS MF3010", mapOf(1L to "Canon"), parts = listOf(ItemRef(7, 2, "Cartridge", "725")))
    private val catalog = FakeCatalogRepository(listOf(printer, cartridge, toner, shoes), listOf(CatalogEntry(m125, 3), CatalogEntry(mf3010)))
    private val items = FakeClientItemsRepository()

    private fun TestScope.vm(clientItemId: Long = 0) =
        ClientItemEditViewModel(SavedStateHandle(mapOf("clientId" to 7L, "clientItemId" to clientItemId)), catalog, items).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    @Test
    fun searchSuggestsMatchingItemsAndPickingTicksItsParts() = runTest {
        val vm = vm()
        assertEquals(listOf(m125, mf3010), vm.uiState.value.suggestions)
        vm.onModelQueryChange("cf283")
        assertEquals(listOf(m125), vm.uiState.value.suggestions)

        vm.onModelPicked(m125)
        val state = vm.uiState.value
        assertEquals(ModelStep.PICKED, state.form.step)
        assertEquals(listOf("CF283A", "CF283X"), state.form.draft.parts.map { it.title })
        assertEquals(listOf(cartridge, toner), state.partTypes)
        assertEquals("HP LaserJet M125", state.title)
    }

    @Test
    fun aNewItemTakesTheTypedNameAndItsTypesFields() = runTest {
        val vm = vm()
        vm.onTypePicked(printer)
        vm.onModelQueryChange("LaserJet P1102")
        vm.onStartNewModel()
        assertEquals(ModelStep.NEW, vm.uiState.value.form.step)
        assertEquals("LaserJet P1102", vm.uiState.value.form.draft.item.name)
        assertEquals(printer.id, vm.uiState.value.form.draft.item.typeId)

        vm.onModelValueChange(1, "HP")
        assertEquals("HP LaserJet P1102", vm.uiState.value.title)
        assertEquals(mapOf(1L to listOf("HP", "Canon")), vm.uiState.value.valueSuggestions)
    }

    @Test
    fun savingWithoutTypeOrNameShowsErrorsAndDoesNotSave() = runTest {
        val vm = vm()
        vm.onSave()
        assertEquals(setOf(ItemDraftError.TYPE_REQUIRED, ItemDraftError.NAME_REQUIRED), vm.uiState.value.form.errors)
        assertTrue(items.saved.isEmpty())
    }

    @Test
    fun aPartIsPickedOrTypedInItsSheetAndSaved() = runTest {
        val vm = vm()
        val closed = mutableListOf<ClientItemEditEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.events.collect { closed += it } }
        vm.onModelPicked(m125)
        vm.onAddPart(toner)
        vm.onPartNameChange("TN-1")
        vm.onSavePart()
        // Tapping a listed part opens it; removing it there takes it off.
        vm.onEditPart(1)
        assertEquals("CF283X", vm.uiState.value.form.partEditor!!.draft.title)
        vm.onRemovePart()
        vm.onPlaceChange("Office")
        vm.onSave()

        val saved = items.saved.single()
        assertEquals(listOf("CF283A", "TN-1"), saved.parts.map { it.title })
        assertEquals(3L, saved.parts.last().typeId)
        assertEquals("Office", saved.place)
        assertEquals(listOf<ClientItemEditEvent>(ClientItemEditEvent.Close), closed)
    }

    @Test
    fun aChildIsFilledInOnTheClientWithItsShoesBySize() = runTest {
        val child = ItemType(10, "Child", fields = listOf(ItemField(11, 10, "Birth", FieldKind.DATE)), hasSerial = false, perClient = true)
        val shoe = ItemType(20, "Shoe", 10, listOf(ItemField(21, 20, "Size", FieldKind.NUMBER), ItemField(22, 20, "For", FieldKind.CHOICE, listOf("Boy", "Girl"))), hasSerial = false)
        val runner = CatalogItem(30, shoe, "Runner", mapOf(21L to "30"))
        catalog.types.value = listOf(printer, cartridge, child, shoe)
        catalog.entries.value = catalog.entries.value + CatalogEntry(runner)
        val vm = vm()

        vm.onTypePicked(child)
        assertEquals(ModelStep.NEW, vm.uiState.value.form.step)
        vm.onModelNameChange("David")
        vm.onModelValueChange(11, "2019-03-12")
        assertEquals(listOf(shoe), vm.uiState.value.partTypes)

        vm.onAddPart(shoe)
        assertEquals(listOf(runner), vm.uiState.value.partChoices)
        vm.onPartValueChange(21, "30")
        vm.onPartValueChange(22, "Boy")
        vm.onSavePart()
        // A second pair, picked from the catalog this time.
        vm.onAddPart(shoe)
        vm.onPartItemPicked(runner)
        vm.onSavePart()
        vm.onSave()

        val saved = items.saved.single()
        assertEquals("David", saved.item.name)
        assertEquals(mapOf(11L to "2019-03-12"), saved.item.values)
        assertEquals(listOf(true, false), saved.parts.map { it.isSpec })
        assertEquals(mapOf(21L to "30", 22L to "Boy"), saved.parts.first().values)
        assertEquals(30L, saved.parts.last().itemId)
    }

    @Test
    fun editingLoadsTheItemAndDeleteAsksFirst() = runTest {
        items.items.value = listOf(ClientItem(4, 7, m125, place = "Office", parts = listOf(ClientItemPart(9, CatalogItem(5, cartridge, "CF283A")))))
        val vm = vm(clientItemId = 4)
        val form = vm.uiState.value.form
        assertEquals(ModelStep.PICKED, form.step)
        assertEquals(listOf(9L to "CF283A"), form.draft.parts.map { it.id to it.title })

        vm.onDeleteClick()
        assertEquals(ClientItemEditDialog.CONFIRM_DELETE, vm.uiState.value.form.dialog)
        vm.onConfirmDelete()
        assertEquals(listOf(4L), items.deleted)
    }

    @Test
    fun aSerialOnAnotherItemIsPointedOut() = runTest {
        val other = ClientItem(9, 3, mf3010, serialNumber = "CNB8K12345")
        items.serialMatches = listOf(SerialMatch(other, "Gamma", exact = true))
        val vm = vm()
        vm.onSerialScanned("S/N: CNB8K12345")
        advanceTimeBy(500)
        assertEquals("CNB8K12345", vm.uiState.value.form.draft.serialNumber)
        assertEquals("Gamma · Canon i-SENSYS MF3010", vm.uiState.value.form.serialTakenBy)

        items.serialMatches = emptyList()
        vm.onSerialChange("X")
        advanceTimeBy(500)
        assertNull(vm.uiState.value.form.serialTakenBy)
    }
}
