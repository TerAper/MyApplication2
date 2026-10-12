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
import com.teraper.printmaster.core.model.LinkDraft
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
        assertEquals(listOf("CF283A", "CF283X"), state.form.draft.chosenParts().map { it.title })
        assertEquals(listOf(cartridge, toner), state.partTypes)
        assertEquals("HP LaserJet M125", state.title)
    }

    @Test
    fun aNewItemTakesTheTypedNameAndItsTypesFields() = runTest {
        val vm = vm()
        vm.onModelQueryChange("LaserJet P1102")
        vm.onStartNewModel()
        assertEquals(ModelStep.NEW, vm.uiState.value.form.step)
        assertEquals("LaserJet P1102", vm.uiState.value.form.draft.item.name)

        vm.onNewModelType(printer.id)
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
    fun aTypedPartIsAddedOfTheChosenPartTypeAndSaved() = runTest {
        val vm = vm()
        val closed = mutableListOf<ClientItemEditEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.events.collect { closed += it } }
        vm.onModelPicked(m125)
        vm.onNewPartType(toner.id)
        vm.onNewPartChange("TN-1")
        vm.onPlaceChange("Office")
        vm.onSave()

        val saved = items.saved.single()
        assertEquals(LinkDraft(typeId = 3, title = "TN-1"), saved.item.parts.last())
        assertEquals(listOf("CF283A", "CF283X", "TN-1"), saved.chosenParts().map { it.title })
        assertEquals("Office", saved.place)
        assertEquals(listOf<ClientItemEditEvent>(ClientItemEditEvent.Close), closed)
    }

    @Test
    fun editingLoadsTheItemAndDeleteAsksFirst() = runTest {
        items.items.value = listOf(ClientItem(4, 7, m125, place = "Office", parts = listOf(ClientItemPart(9, CatalogItem(5, cartridge, "CF283A")))))
        val vm = vm(clientItemId = 4)
        val form = vm.uiState.value.form
        assertEquals(ModelStep.PICKED, form.step)
        assertEquals(listOf("CF283A"), form.draft.chosenParts().map { it.title })

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
