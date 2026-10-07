package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.DeleteModelResult
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflinePrintersRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveModelResult
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrinterDraftError
import com.teraper.printmaster.core.model.PrinterModelDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class OfflineCatalogRepositoryTest {

    private lateinit var db: PrintMasterDatabase
    private lateinit var catalog: OfflineCatalogRepository
    private lateinit var printers: OfflinePrintersRepository
    private var clientId = 0L

    @Before
    fun setUp() = runTest {
        db = PrintMasterDatabase.create(ApplicationProvider.getApplicationContext(), inMemory = true)
        val writer = CatalogWriter(db.catalogDao())
        catalog = OfflineCatalogRepository(db, db.catalogDao(), writer)
        printers = OfflinePrintersRepository(db, db.catalogDao(), writer)
        val clients = OfflineClientsRepository(db, db.clientDao(), Clock.systemUTC(), OfflineCompaniesRepository(db, db.companyDao(), Clock.systemUTC()))
        clientId = (clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
    }

    @After
    fun tearDown() = db.close()

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private val m125 = PrinterModelDraft(
        brand = "HP",
        name = "LaserJet M125",
        cartridges = listOf(CartridgeDraft("CF283A", "83A chip")),
    )

    private suspend fun addPrinter(model: PrinterModelDraft, location: String = ""): Long {
        val draft = ClientPrinterDraft(
            clientId = clientId,
            model = model,
            selectedCartridges = model.cartridges.mapTo(mutableSetOf()) { it.key },
            location = location,
        )
        return (printers.savePrinter(draft) as SavePrinterResult.Saved).printerId
    }

    @Test
    fun newPrinterAddsModelCartridgesAndChipsToCatalog() = runTest {
        addPrinter(m125, location = "Accounting")

        val printer = printers.observeClientPrinters(clientId).first().single()
        assertEquals("HP LaserJet M125", printer.model.fullName)
        assertEquals("Accounting", printer.location)
        assertEquals(listOf("CF283A"), printer.cartridges.map { it.cartridge.name })
        assertEquals(listOf("83A chip"), printer.cartridges.single().cartridge.chips.map { it.name })

        val entry = catalog.observeCatalog().first().single()
        assertEquals(1, entry.printerCount)
        assertEquals(listOf("CF283A"), entry.model.cartridges.map { it.name })
    }

    @Test
    fun typingSameNamesDifferentlyReusesCatalogEntries() = runTest {
        addPrinter(m125)
        addPrinter(PrinterModelDraft(brand = "hp ", name = "laserjet  m125", cartridges = listOf(CartridgeDraft("cf 283a"))))

        assertEquals(1, count("brands"))
        assertEquals(1, count("printer_models"))
        assertEquals(1, count("cartridges"))
        assertEquals(2, catalog.observeCatalog().first().single().printerCount)
    }

    @Test
    fun editingPrinterKeepsCartridgeRowIds() = runTest {
        val id = addPrinter(m125)
        val before = printers.getPrinter(id)!!
        val keptRow = before.cartridges.single().id

        val draft = ClientPrinterDraft.from(before).addCartridge(CartridgeDraft("CF283X")).copy(location = "Director")
        printers.savePrinter(draft)

        val after = printers.getPrinter(id)!!
        assertEquals("Director", after.location)
        assertEquals(listOf("CF283A", "CF283X"), after.cartridges.map { it.cartridge.name })
        assertEquals(keptRow, after.cartridges.first().id)
        // The new cartridge was also added to the catalog model.
        assertEquals(2, catalog.observeCatalog().first().single().model.cartridges.size)
    }

    @Test
    fun modelInUseCannotBeDeletedButUnusedOneCanAndLeavesNoOrphans() = runTest {
        val printerId = addPrinter(m125)
        val modelId = catalog.observeCatalog().first().single().model.id

        assertEquals(DeleteModelResult.IN_USE, catalog.deleteModel(modelId))
        assertTrue(printers.deletePrinter(printerId))
        assertEquals(DeleteModelResult.DELETED, catalog.deleteModel(modelId))

        assertEquals(0, count("brands"))
        assertEquals(0, count("cartridges"))
        assertEquals(0, count("chips"))
    }

    @Test
    fun catalogEditSetsCartridgesAndChipsExactly() = runTest {
        val id = (catalog.saveModel(m125) as SaveModelResult.Saved).modelId
        val edited = PrinterModelDraft.from(catalog.observeModel(id).first()!!).copy(
            colorType = ColorType.COLOR,
            cartridges = listOf(CartridgeDraft("CF283X", "new chip")),
        )
        catalog.saveModel(edited)

        val model = catalog.observeModel(id).first()!!
        assertEquals(ColorType.COLOR, model.colorType)
        assertEquals(listOf("CF283X"), model.cartridges.map { it.name })
        assertEquals(listOf("new chip"), model.cartridges.single().chips.map { it.name })
        assertEquals(1, count("cartridges"))
        assertEquals(1, count("chips"))
    }

    @Test
    fun catalogEditFixesCartridgeSpellingButPrinterFormDoesNot() = runTest {
        addPrinter(m125.copy(cartridges = listOf(CartridgeDraft("cf283a"))))
        addPrinter(m125.copy(cartridges = listOf(CartridgeDraft("CF 283A"))))
        val model = catalog.observeCatalog().first().single().model
        assertEquals(listOf("cf283a"), model.cartridges.map { it.name })

        catalog.saveModel(PrinterModelDraft.from(model).copy(cartridges = listOf(CartridgeDraft("CF283A"))))
        assertEquals(listOf("CF283A"), catalog.observeModel(model.id).first()!!.cartridges.map { it.name })
        assertEquals(1, count("cartridges"))
    }

    @Test
    fun duplicateModelNameAndMissingFieldsAreRejected() = runTest {
        catalog.saveModel(m125)
        assertEquals(SaveModelResult.NameTaken, catalog.saveModel(PrinterModelDraft(brand = "Hp", name = "LASERJET M125")))
        assertEquals(
            SaveModelResult.Invalid(setOf(PrinterDraftError.MODEL_REQUIRED)),
            catalog.saveModel(PrinterModelDraft(brand = "HP")),
        )
        assertEquals(1, count("printer_models"))
    }

    @Test
    fun ownersListClientsWithThatModel() = runTest {
        addPrinter(m125, location = "Office")
        val modelId = catalog.observeCatalog().first().single().model.id

        val owner = catalog.observeModelOwners(modelId).first().single()
        assertEquals(clientId, owner.clientId)
        assertEquals("Firm", owner.clientName)
        assertEquals("Office", owner.location)
        assertEquals(CatalogNames.codeKey("CF283A"), m125.cartridges.single().key)
    }
}
