package com.teraper.printmaster.feature.catalog

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.data.repository.SaveItemResult
import com.teraper.printmaster.core.model.CatalogEntry
import com.teraper.printmaster.core.model.CatalogItem
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemOwner
import com.teraper.printmaster.core.model.ItemRef
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.ItemTypeError
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.core.model.TypeTemplate
import com.teraper.printmaster.core.testing.FakeCatalogRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.catalog.item.ItemEditDialog
import com.teraper.printmaster.feature.catalog.item.ItemEditEvent
import com.teraper.printmaster.feature.catalog.item.ItemEditViewModel
import com.teraper.printmaster.feature.catalog.list.CatalogViewModel
import com.teraper.printmaster.feature.catalog.types.ItemTypesViewModel
import com.teraper.printmaster.feature.catalog.types.TypeEditEvent
import com.teraper.printmaster.feature.catalog.types.TypeEditViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val printer = ItemType(
        1, "Printer",
        fields = listOf(ItemField(1, 1, "Brand", inTitle = true), ItemField(2, 1, "Print", FieldKind.CHOICE, listOf("Laser", "Inkjet"))),
    )
    private val cartridge = ItemType(2, "Cartridge", parentId = 1)
    private val chip = ItemType(3, "Chip", parentId = 2)

    private fun model(id: Long, brand: String, name: String, vararg parts: String) = CatalogItem(
        id, printer, name, mapOf(1L to brand), parts = parts.mapIndexed { i, p -> ItemRef(id * 10 + i, 2, "Cartridge", p) },
    )

    private val ce285 = CatalogItem(20, cartridge, "CE285A", fitsInto = listOf(ItemRef(3, 1, "Printer", "HP P1102")))
    private val repo = FakeCatalogRepository(
        listOf(printer, cartridge, chip),
        listOf(
            CatalogEntry(model(1, "Canon", "MF3010", "725")),
            CatalogEntry(model(2, "HP", "M125", "CF283A"), ownedCount = 2),
            CatalogEntry(model(3, "HP", "P1102", "CE285A")),
            CatalogEntry(ce285),
        ),
    )

    private fun TestScope.itemVm(itemId: Long = 0, typeId: Long = 0) =
        ItemEditViewModel(SavedStateHandle(mapOf("itemId" to itemId, "typeId" to typeId)), repo).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    private fun TestScope.typeVm(typeId: Long = 0) =
        TypeEditViewModel(SavedStateHandle(mapOf("typeId" to typeId)), repo).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        }

    @Test
    fun catalogGroupsByTypeFiltersAndSearchesParts() = runTest {
        val vm = CatalogViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }

        assertEquals(listOf("Printer", "Cartridge"), vm.uiState.value.groups.map { it.type.name })
        assertEquals(3, vm.uiState.value.groups[0].items.size)

        vm.onQueryChange("ce285")
        assertEquals(listOf("HP P1102", "CE285A"), vm.uiState.value.groups.flatMap { it.items }.map { it.item.title })

        vm.onTypeFilter(cartridge.id)
        assertEquals(listOf("CE285A"), vm.uiState.value.groups.flatMap { it.items }.map { it.item.title })
        assertEquals(4, vm.uiState.value.totalCount)
    }

    @Test
    fun anItemShowsWhatItGoesIntoAndOneSectionPerPartType() = runTest {
        val vm = itemVm(itemId = 2)
        val sections = vm.uiState.value.sections
        assertEquals(listOf("Cartridge"), sections.map { it.type.name })
        assertEquals(listOf("CF283A"), sections.single().links.map { it.title })
        // Other cartridges in the catalog are offered, not ones already linked.
        assertEquals(listOf("CE285A"), sections.single().candidates.map { it.title })

        val cartridgeVm = itemVm(itemId = 20)
        assertEquals(listOf("Printer" to true, "Chip" to false), cartridgeVm.uiState.value.sections.map { it.type.name to it.isParent })
    }

    @Test
    fun aTypedPartIsAddedOnSaveAndTheItemSaved() = runTest {
        val vm = itemVm(itemId = 2)
        val section = vm.uiState.value.sections.single()
        vm.onLinkQueryChange(cartridge.id, "CF283X")
        vm.onSave()

        assertEquals(ItemEditEvent.Close, vm.events.first())
        assertEquals(listOf("CF283A", "CF283X"), repo.savedItems.single().parts.map { it.title })
        assertEquals(LinkDraft(typeId = 2, title = "CF283X"), repo.savedItems.single().parts.last())
        assertEquals(cartridge, section.type)
    }

    @Test
    fun aNewItemOfAKnownTypeUsesItsFieldsAndBrandsUsedBefore() = runTest {
        val vm = itemVm(typeId = printer.id)
        assertEquals(printer, vm.uiState.value.type)
        assertEquals(mapOf(1L to listOf("HP", "Canon")), vm.uiState.value.suggestions)
        vm.onValueChange(1, "HP")
        vm.onNameChange("M15")
        assertEquals("HP M15", vm.uiState.value.title)
    }

    @Test
    fun takenNameIsShownUntilNameChanges() = runTest {
        repo.saveItemResult = SaveItemResult.NameTaken
        val vm = itemVm(typeId = printer.id)
        vm.onValueChange(1, "HP")
        vm.onNameChange("M125")
        vm.onSave()
        assertTrue(vm.uiState.value.form.nameTaken)

        vm.onNameChange("M126")
        assertFalse(vm.uiState.value.form.nameTaken)
    }

    @Test
    fun itemOwnedByClientsCannotBeDeleted() = runTest {
        repo.owners.value = listOf(ItemOwner(1, "Firm", ""))
        val vm = itemVm(itemId = 2)
        vm.onDeleteClick()
        assertEquals(ItemEditDialog.IN_USE, vm.uiState.value.form.dialog)
    }

    @Test
    fun typeFormKeepsOneEmptyFieldAndCantGoIntoItsOwnParts() = runTest {
        val vm = typeVm(typeId = printer.id)
        assertEquals(listOf("Brand", "Print", ""), vm.uiState.value.form.draft.fields.map { it.name })
        // A printer can't be part of a cartridge or a chip: that would be a loop.
        assertEquals(emptyList<ItemType>(), vm.uiState.value.parents)

        vm.onFieldChange(2, FieldDraft(name = "Wi-Fi", kind = FieldKind.YES_NO))
        assertEquals(listOf("Brand", "Print", "Wi-Fi", ""), vm.uiState.value.form.draft.fields.map { it.name })
        vm.onSave()
        assertEquals(TypeEditEvent.Close, vm.events.first())
        assertEquals(listOf("Brand", "Print", "Wi-Fi"), repo.savedTypes.single().normalized().fields.map { it.name })

        assertEquals(listOf(printer, cartridge), typeVm(typeId = chip.id).uiState.value.parents)
    }

    @Test
    fun aChoiceFieldNeedsItsOptions() = runTest {
        val vm = typeVm()
        vm.onNameChange("Shirt")
        vm.onFieldChange(0, FieldDraft(name = "Size", kind = FieldKind.CHOICE))
        vm.onSave()
        assertEquals(setOf(ItemTypeError.CHOICES_REQUIRED), vm.uiState.value.form.errors)
        assertTrue(repo.savedTypes.isEmpty())
    }

    @Test
    fun pickingATemplateAddsItsTypes() = runTest {
        val vm = ItemTypesViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { vm.uiState.collect {} }
        assertEquals(listOf("Printer" to 0, "Cartridge" to 1, "Chip" to 2), vm.uiState.value.types.map { it.type.name to it.depth })

        val shoes = listOf(TypeTemplate("Shoes", fields = listOf(FieldDraft(name = "Size", kind = FieldKind.NUMBER))))
        vm.onShowTemplates()
        vm.onTemplatePicked(ItemTemplate("shoes", "Shoes", shoes))
        assertEquals(listOf(shoes), repo.templates)
        assertEquals(1, vm.uiState.value.added)
        assertFalse(vm.uiState.value.showTemplates)
    }
}
