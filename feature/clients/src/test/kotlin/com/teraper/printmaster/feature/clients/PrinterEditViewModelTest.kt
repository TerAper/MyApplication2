package com.teraper.printmaster.feature.clients

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.Cartridge
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.ClientPrinterCartridge
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.feature.clients.printer.ModelStep
import com.teraper.printmaster.feature.clients.printer.PrinterEditDialog
import com.teraper.printmaster.feature.clients.printer.PrinterEditEvent
import com.teraper.printmaster.feature.clients.printer.PrinterEditViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterEditViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val m125 = PrinterModel(
        1, 1, "HP", "LaserJet M125", PrintType.LASER, ColorType.MONO,
        listOf(Cartridge(1, "CF283A"), Cartridge(2, "CF283X")),
    )
    private val mf3010 = PrinterModel(2, 2, "Canon", "i-SENSYS MF3010", PrintType.LASER, ColorType.MONO, listOf(Cartridge(3, "725")))
    private val catalog = FakeCatalogRepository(listOf(m125, mf3010))
    private val printers = FakePrintersRepository()

    private fun TestScope.vm(printerId: Long = 0) =
        PrinterEditViewModel(SavedStateHandle(mapOf("clientId" to 7L, "printerId" to printerId)), catalog, printers).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    @Test
    fun searchSuggestsMatchingModelsAndPickingTicksItsCartridges() = runTest {
        val vm = vm()
        vm.onModelQueryChange("cf283")
        assertEquals(listOf(m125), vm.uiState.value.suggestions)

        vm.onModelPicked(m125)
        val form = vm.uiState.value.form
        assertEquals(ModelStep.PICKED, form.step)
        assertEquals(listOf("CF283A", "CF283X"), form.draft.chosenCartridges().map { it.name })
    }

    @Test
    fun newModelGuessesKnownBrandFromQuery() = runTest {
        val vm = vm()
        vm.onModelQueryChange("hp laserjet p1102")
        vm.onStartNewModel()

        val model = vm.uiState.value.form.draft.model
        assertEquals(ModelStep.NEW, vm.uiState.value.form.step)
        assertEquals("HP", model.brand)
        assertEquals("laserjet p1102", model.name)
        assertEquals(0L, model.id)
    }

    @Test
    fun savingWithoutModelShowsErrorsAndDoesNotSave() = runTest {
        val vm = vm()
        vm.onSave()
        assertEquals(setOf(PrinterDraftError.BRAND_REQUIRED, PrinterDraftError.MODEL_REQUIRED), vm.uiState.value.form.errors)
        assertTrue(printers.saved.isEmpty())
    }

    @Test
    fun typedButNotAddedCartridgeIsSavedToo() = runTest {
        val vm = vm()
        vm.onModelPicked(mf3010)
        vm.onCartridgeToggle(CatalogNames.codeKey("725"))
        vm.onNewCartridgeChange(CartridgeDraft("CRG-737", "chip 737"))
        vm.onLocationChange("Office")
        vm.onSave()

        assertEquals(PrinterEditEvent.Close, vm.events.first())
        val saved = printers.saved.single()
        assertEquals(7L, saved.clientId)
        assertEquals(listOf("CRG-737"), saved.chosenCartridges().map { it.name })
        assertEquals("Office", saved.location)
    }

    @Test
    fun editLoadsPrinterAndDeleteAsksFirst() = runTest {
        printers.printers.value = listOf(
            ClientPrinter(5, 7, m125, location = "Director", cartridges = listOf(ClientPrinterCartridge(9, Cartridge(1, "CF283A")))),
        )
        val vm = vm(printerId = 5)
        val form = vm.uiState.value.form
        assertEquals(ModelStep.PICKED, form.step)
        assertEquals("Director", form.draft.location)
        assertEquals(listOf("CF283A"), form.draft.chosenCartridges().map { it.name })

        vm.onDeleteClick()
        assertEquals(PrinterEditDialog.CONFIRM_DELETE, vm.uiState.value.form.dialog)
        vm.onConfirmDelete()
        assertEquals(PrinterEditEvent.Close, vm.events.first())
        assertEquals(listOf(5L), printers.deleted)
    }

    @Test
    fun closingWithChangesAsksToDiscard() = runTest {
        val vm = vm()
        vm.onLocationChange("x")
        vm.onCloseRequest()
        assertEquals(PrinterEditDialog.DISCARD, vm.uiState.value.form.dialog)
    }
}
