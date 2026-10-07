package com.teraper.printmaster.feature.catalog

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.data.repository.CatalogRepository
import com.teraper.printmaster.core.data.repository.DeleteModelResult
import com.teraper.printmaster.core.data.repository.SaveModelResult
import com.teraper.printmaster.core.model.Cartridge
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogModel
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.ModelOwner
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.feature.catalog.list.CatalogViewModel
import com.teraper.printmaster.feature.catalog.model.ModelEditDialog
import com.teraper.printmaster.feature.catalog.model.ModelEditEvent
import com.teraper.printmaster.feature.catalog.model.ModelEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

private class FakeCatalogRepository(models: List<CatalogModel>) : CatalogRepository {
    val catalog = MutableStateFlow(models)
    val owners = MutableStateFlow<List<ModelOwner>>(emptyList())
    val saved = mutableListOf<PrinterModelDraft>()
    var saveResult: SaveModelResult = SaveModelResult.Saved(1)

    override fun observeCatalog(): Flow<List<CatalogModel>> = catalog
    override fun observeModel(id: Long): Flow<PrinterModel?> = catalog.map { list -> list.firstOrNull { it.model.id == id }?.model }
    override fun observeModelOwners(modelId: Long): Flow<List<ModelOwner>> = owners
    override suspend fun saveModel(draft: PrinterModelDraft): SaveModelResult {
        saved += draft
        return saveResult
    }
    override suspend fun deleteModel(id: Long): DeleteModelResult = DeleteModelResult.DELETED
}

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private fun model(id: Long, brandId: Long, brand: String, name: String, vararg cartridges: String) = PrinterModel(
        id, brandId, brand, name, PrintType.LASER, ColorType.MONO, cartridges.mapIndexed { i, c -> Cartridge(id * 10 + i, c) },
    )

    private val repo = FakeCatalogRepository(
        listOf(
            CatalogModel(model(1, 1, "Canon", "MF3010", "725")),
            CatalogModel(model(2, 2, "HP", "M125", "CF283A"), printerCount = 2),
            CatalogModel(model(3, 2, "HP", "P1102", "CE285A")),
        ),
    )

    private fun TestScope.editVm(modelId: Long = 0) =
        ModelEditViewModel(SavedStateHandle(mapOf("modelId" to modelId)), repo).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    @Test
    fun catalogGroupsByBrandAndSearchesCartridges() = runTest {
        val vm = CatalogViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(listOf("Canon", "HP"), vm.uiState.value.groups.map { it.brand })
        assertEquals(2, vm.uiState.value.groups[1].models.size)

        vm.onQueryChange("ce285")
        assertEquals(listOf("P1102"), vm.uiState.value.groups.flatMap { it.models }.map { it.model.name })
        assertEquals(3, vm.uiState.value.totalCount)
    }

    @Test
    fun editKeepsOneEmptyCartridgeRowAndSavesWithoutIt() = runTest {
        val vm = editVm(modelId = 2)
        assertEquals(listOf("CF283A", ""), vm.uiState.value.form.draft.cartridges.map { it.name })

        vm.onCartridgeChange(1, CartridgeDraft("CF283X"))
        assertEquals(listOf("CF283A", "CF283X", ""), vm.uiState.value.form.draft.cartridges.map { it.name })

        vm.onSave()
        assertEquals(ModelEditEvent.Close, vm.events.first())
        assertEquals(listOf("CF283A", "CF283X"), repo.saved.single().normalized().cartridges.map { it.name })
    }

    @Test
    fun takenNameIsShownUntilNameChanges() = runTest {
        repo.saveResult = SaveModelResult.NameTaken
        val vm = editVm()
        vm.onBrandChange("HP")
        vm.onNameChange("M125")
        vm.onSave()
        assertTrue(vm.uiState.value.form.nameTaken)

        vm.onNameChange("M126")
        assertTrue(!vm.uiState.value.form.nameTaken)
    }

    @Test
    fun modelOwnedByClientsCannotBeDeleted() = runTest {
        repo.owners.value = listOf(ModelOwner(1, "Firm", ""))
        val vm = editVm(modelId = 2)
        vm.onDeleteClick()
        assertEquals(ModelEditDialog.IN_USE, vm.uiState.value.form.dialog)
    }
}
