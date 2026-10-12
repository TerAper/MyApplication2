package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.DeleteResult
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientItemsRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.SaveClientItemResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveItemResult
import com.teraper.printmaster.core.data.repository.SaveTypeResult
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemDraftError
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.core.model.TypeTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class OfflineCatalogRepositoryTest {

    private lateinit var db: PrintMasterDatabase
    private lateinit var catalog: OfflineCatalogRepository
    private lateinit var items: OfflineClientItemsRepository
    private var clientId = 0L
    private var printer = 0L
    private var cartridge = 0L
    private var chip = 0L
    private var brand = 0L
    private var print = 0L

    @Before
    fun setUp() = runTest {
        db = PrintMasterDatabase.create(ApplicationProvider.getApplicationContext(), inMemory = true)
        val writer = CatalogWriter(db.catalogDao())
        catalog = OfflineCatalogRepository(db, db.catalogDao(), writer)
        items = OfflineClientItemsRepository(db, db.catalogDao(), writer)
        val clients = OfflineClientsRepository(db, db.clientDao(), Clock.systemUTC(), OfflineCompaniesRepository(db, db.companyDao(), Clock.systemUTC()))
        clientId = (clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId

        printer = saveType(
            ItemTypeDraft(
                name = "Printer",
                fields = listOf(FieldDraft(name = "Brand", inTitle = true), FieldDraft(name = "Print", kind = FieldKind.CHOICE, options = "Laser\nInkjet")),
            ),
        )
        cartridge = saveType(ItemTypeDraft(name = "Cartridge", parentId = printer))
        chip = saveType(ItemTypeDraft(name = "Chip", parentId = cartridge))
        val fields = catalog.observeTypes().first().first { it.id == printer }.fields
        brand = fields[0].id
        print = fields[1].id
    }

    @After
    fun tearDown() = db.close()

    private suspend fun saveType(draft: ItemTypeDraft) = (catalog.saveType(draft) as SaveTypeResult.Saved).typeId

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun m125() = ItemDraft(
        typeId = printer,
        name = "LaserJet M125",
        values = mapOf(brand to "HP", print to "Laser"),
        parts = listOf(LinkDraft(typeId = cartridge, title = "CF283A")),
    )

    private suspend fun addItem(model: ItemDraft, place: String = "", serial: String = ""): Long {
        val draft = ClientItemDraft(
            clientId = clientId,
            item = model,
            selectedParts = model.parts.mapTo(mutableSetOf()) { it.key },
            place = place,
            serialNumber = serial,
        )
        return (items.saveClientItem(draft) as SaveClientItemResult.Saved).clientItemId
    }

    @Test
    fun typesKeepTheirFieldsInOrderAndTheirTree() = runTest {
        val types = catalog.observeTypes().first()
        assertEquals(listOf("Printer", "Cartridge", "Chip"), types.map { it.name })
        assertEquals(listOf("Brand", "Print"), types.first().fields.map { it.name })
        assertEquals(listOf("Laser", "Inkjet"), types.first().fields[1].options)
        assertEquals(SaveTypeResult.NameTaken, catalog.saveType(ItemTypeDraft(name = " printer ")))

        // A type can't go into one of its own parts: the loop is dropped.
        catalog.saveType(ItemTypeDraft(id = printer, name = "Printer", parentId = chip, fields = ItemTypeDraft.from(types.first()).fields))
        assertNull(catalog.observeTypes().first().first { it.id == printer }.parentId)
    }

    @Test
    fun aRemovedFieldTakesItsValuesWithIt() = runTest {
        val id = (catalog.saveItem(m125()) as SaveItemResult.Saved).itemId
        val type = catalog.observeTypes().first().first { it.id == printer }
        catalog.saveType(ItemTypeDraft.from(type).copy(fields = ItemTypeDraft.from(type).fields.take(1)))

        val item = catalog.observeItem(id).first()!!
        assertEquals(mapOf(brand to "HP"), item.values)
        assertEquals("HP LaserJet M125", item.title)
    }

    @Test
    fun newClientItemAddsModelAndPartsToTheCatalog() = runTest {
        addItem(m125(), place = "Accounting")

        val owned = items.observeClientItems(clientId).first().single()
        assertEquals("HP LaserJet M125", owned.item.title)
        assertEquals("Laser", owned.item.details)
        assertEquals("Accounting", owned.place)
        assertEquals(listOf("CF283A"), owned.parts.map { it.item.title })

        val entries = catalog.observeCatalog().first()
        assertEquals(listOf("HP LaserJet M125", "CF283A"), entries.map { it.item.title })
        assertEquals(1, entries.first().ownedCount)
        assertEquals(listOf("HP LaserJet M125"), entries.last().item.fitsInto.map { it.title })
    }

    @Test
    fun typingSameNamesDifferentlyReusesCatalogItems() = runTest {
        addItem(m125())
        addItem(
            ItemDraft(
                typeId = printer, name = "laserjet  m125", values = mapOf(brand to "hp"),
                parts = listOf(LinkDraft(typeId = cartridge, title = "cf 283a")),
            ),
        )
        assertEquals(2, count("items"))
        assertEquals(2, catalog.observeCatalog().first().first().ownedCount)
    }

    @Test
    fun editingAClientItemKeepsPartRowIds() = runTest {
        val id = addItem(m125())
        val before = items.getClientItem(id)!!
        val keptRow = before.parts.single().id

        val draft = ClientItemDraft.from(before).addPart(LinkDraft(typeId = cartridge, title = "CF283X")).copy(place = "Director")
        items.saveClientItem(draft)

        val after = items.getClientItem(id)!!
        assertEquals("Director", after.place)
        assertEquals(listOf("CF283A", "CF283X"), after.parts.map { it.item.title })
        assertEquals(keptRow, after.parts.first().id)
        // The new part was also added to the catalog model.
        assertEquals(2, catalog.observeCatalog().first().first { it.item.typeId == printer }.item.parts.size)
    }

    @Test
    fun itemsInUseCannotBeDeletedButUnusedOnesCan() = runTest {
        val clientItemId = addItem(m125())
        val entries = catalog.observeCatalog().first()
        val modelId = entries.first { it.item.typeId == printer }.item.id
        val partId = entries.first { it.item.typeId == cartridge }.item.id

        assertEquals(DeleteResult.IN_USE, catalog.deleteItem(modelId))
        assertEquals(DeleteResult.IN_USE, catalog.deleteItem(partId))
        assertTrue(items.deleteClientItem(clientItemId))
        assertEquals(DeleteResult.DELETED, catalog.deleteItem(modelId))
        assertEquals(0, count("item_fits"))
        assertEquals(0, count("item_values"))
        assertEquals(DeleteResult.IN_USE, catalog.deleteType(cartridge))
        assertEquals(DeleteResult.DELETED, catalog.deleteItem(partId))
        assertEquals(DeleteResult.DELETED, catalog.deleteType(cartridge))
        // Its part type now stands alone.
        assertNull(catalog.observeTypes().first().first { it.id == chip }.parentId)
    }

    @Test
    fun catalogEditSetsLinksExactlyBothWays() = runTest {
        val id = (catalog.saveItem(m125()) as SaveItemResult.Saved).itemId
        val chipX = (catalog.saveItem(ItemDraft(typeId = chip, name = "Chip X")) as SaveItemResult.Saved).itemId
        catalog.saveItem(ItemDraft.from(catalog.observeItem(id).first()!!).copy(parts = listOf(LinkDraft(typeId = cartridge, title = "CF283X"))))

        val model = catalog.observeItem(id).first()!!
        assertEquals(listOf("CF283X"), model.parts.map { it.title })
        // The old cartridge stays in the catalog, just no longer in this model.
        assertEquals(2, catalog.observeCatalog().first().count { it.item.typeId == cartridge })

        val cf283x = model.parts.single().id
        catalog.saveItem(
            ItemDraft.from(catalog.observeItem(cf283x).first()!!).copy(parts = listOf(LinkDraft(chipX, chip, "Chip X"))),
        )
        val withChip = catalog.observeItem(cf283x).first()!!
        assertEquals(listOf("HP LaserJet M125"), withChip.fitsInto.map { it.title })
        assertEquals(listOf("Chip X"), withChip.parts.map { it.title })

        // Removing what it goes into from the cartridge's side unlinks it from the printer too.
        catalog.saveItem(ItemDraft.from(withChip).copy(fitsInto = emptyList()))
        assertTrue(catalog.observeItem(id).first()!!.parts.isEmpty())
    }

    @Test
    fun duplicateTitleAndMissingFieldsAreRejected() = runTest {
        catalog.saveItem(m125())
        assertEquals(SaveItemResult.NameTaken, catalog.saveItem(ItemDraft(typeId = printer, name = "LASERJET M125", values = mapOf(brand to "Hp"))))
        // Same name, other brand: a different model.
        assertTrue(catalog.saveItem(ItemDraft(typeId = printer, name = "LaserJet M125", values = mapOf(brand to "Canon"))) is SaveItemResult.Saved)
        assertEquals(SaveItemResult.Invalid(setOf(ItemDraftError.NAME_REQUIRED)), catalog.saveItem(ItemDraft(typeId = printer)))
        // Two printers and the first one's cartridge.
        assertEquals(3, count("items"))
    }

    @Test
    fun ownersListClientsWithThatItem() = runTest {
        addItem(m125(), place = "Office")
        val modelId = catalog.observeCatalog().first().first().item.id

        val owner = catalog.observeOwners(modelId).first().single()
        assertEquals(clientId, owner.clientId)
        assertEquals("Firm", owner.clientName)
        assertEquals("Office", owner.place)
    }

    @Test
    fun aTemplateAddsOnlyWhatIsMissing() = runTest {
        val added = catalog.applyTemplate(
            listOf(
                TypeTemplate("printer", fields = listOf(FieldDraft(name = "brand"), FieldDraft(name = "Wi-Fi", kind = FieldKind.YES_NO))),
                TypeTemplate("Toner", parentName = "Printer"),
                TypeTemplate("Shoes", fields = listOf(FieldDraft(name = "Size", kind = FieldKind.NUMBER))),
            ),
        )
        assertEquals(2, added)
        val types = catalog.observeTypes().first()
        assertEquals(listOf("Brand", "Print", "Wi-Fi"), types.first { it.id == printer }.fields.map { it.name })
        assertEquals(printer, types.first { it.name == "Toner" }.parentId)
        assertEquals(listOf("Printer", "Cartridge", "Chip", "Toner", "Shoes"), types.map { it.name })
    }

    @Test
    fun serialNumbersAreSavedCleanAndFoundHoweverTheyAreTyped() = runTest {
        val id = addItem(m125(), serial = "S/N: cnb8 k12345")
        addItem(m125(), place = "Office", serial = "CNB8K99999")
        assertEquals("cnb8 k12345", items.getClientItem(id)!!.serialNumber)

        val exact = items.findBySerial("CNB8K12345")
        assertEquals(listOf(id), exact.map { it.item.id })
        assertTrue(exact.single().exact)
        assertEquals("Firm", exact.single().clientName)
        // Part of the label read: both items whose serial contains it, none of them exact.
        val partial = items.findBySerial("cnb8k")
        assertEquals(2, partial.size)
        assertTrue(partial.none { it.exact })
        assertTrue(items.findBySerial("ZZZZ1").isEmpty())

        // Cleared serial: no longer found.
        items.saveClientItem(ClientItemDraft.from(items.getClientItem(id)!!).copy(serialNumber = " "))
        assertTrue(items.findBySerial("CNB8K12345").isEmpty())
    }
}
