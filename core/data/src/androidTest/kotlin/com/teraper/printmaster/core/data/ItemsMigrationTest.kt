package com.teraper.printmaster.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientItemsRepository
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.FieldKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A version-13 database full of printers is opened by today's app: everything moves into item types.
 * assets/schema-13.json is a copy of core/database/schemas/…/13.json, the tables as they were then.
 */
@RunWith(AndroidJUnit4::class)
class ItemsMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = context.getDatabasePath(PrintMasterDatabase.DATABASE_NAME)

    @Before
    fun setUp() {
        context.deleteDatabase(PrintMasterDatabase.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("schema-13.json").bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            oldData(db)
            db.version = 13
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrintMasterDatabase.DATABASE_NAME)
    }

    /** Two printer models, a cartridge with a chip, a client's printer with work, photos and prices. */
    private fun oldData(db: SQLiteDatabase) {
        listOf(
            "INSERT INTO companies (id, name, bank_accounts, color_index, created_at) VALUES (1, 'Main', '', 0, 0)",
            "INSERT INTO clients (id, name, type, note, created_at) VALUES (1, 'Gamma', 'FIRM', '', 0)",
            "INSERT INTO brands (id, name) VALUES (1, 'HP'), (2, 'Canon')",
            "INSERT INTO printer_models (id, brand_id, name, print_type, color_type) VALUES (1, 1, 'LaserJet M125', 'LASER', 'MONO'), (2, 2, 'Pixma G3410', 'INK', 'COLOR')",
            "INSERT INTO cartridges (id, name) VALUES (1, 'CF283A')",
            "INSERT INTO chips (id, name) VALUES (1, '83A chip')",
            "INSERT INTO model_cartridges (model_id, cartridge_id) VALUES (1, 1)",
            "INSERT INTO cartridge_chips (cartridge_id, chip_id) VALUES (1, 1)",
            "INSERT INTO client_printers (id, client_id, model_id, location, note, sync_id, serial_number, serial_key) VALUES (7, 1, 1, 'Office', 'old one', 's7', 'cnb 123', 'CNB123')",
            "INSERT INTO client_printer_cartridges (id, client_printer_id, cartridge_id, note) VALUES (9, 7, 1, '')",
            "INSERT INTO repair_parts (id, category, name, description, price_minor, cost_minor, archived) VALUES " +
                "(1, 'CARTRIDGE', 'Refill', '', 300000, 90000, 0), (2, 'PRINTER', 'Drum', '', 800000, 0, 0), (3, 'OTHER', 'Visit', '', 100000, 0, 0)",
            "INSERT INTO orders (id, company_id, client_id, scheduled_at, description, status, created_at) VALUES (1, 1, 1, 0, 'Refill', 'DONE', 0)",
            "INSERT INTO repairs (id, order_id, client_printer_id, client_printer_cartridge_id, note, created_at) VALUES (1, 1, 7, 9, 'ok', 0)",
            "INSERT INTO repair_items (id, repair_id, part_id, name, price_minor, cost_minor, quantity) VALUES (1, 1, 1, 'Refill', 300000, 90000, 2)",
            "INSERT INTO photos (id, owner_type, owner_id, file_name, created_at) VALUES (1, 'PRINTER', 7, 'a', 0), (2, 'CARTRIDGE', 9, 'b', 0), (3, 'ORDER', 1, 'c', 0)",
            "INSERT INTO expenses (id, company_id, date_epoch_day, amount_minor, category, note, created_at) VALUES (1, 1, 0, 100, 'TONER', '', 0), (2, 1, 0, 100, 'PARTS', '', 0)",
        ).forEach(db::execSQL)
    }

    private fun PrintMasterDatabase.rows(sql: String): List<String> = openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    @Test
    fun printersBecomeItemsAndNothingIsLost() = runTest {
        val db = PrintMasterDatabase.create(context)
        try {
            val writer = CatalogWriter(db.catalogDao())
            val catalog = OfflineCatalogRepository(db, db.catalogDao(), writer)
            val clientItems = OfflineClientItemsRepository(db, db.catalogDao(), writer)

            // Types: Printer → Cartridge → Chip, with the old printer columns as fields.
            val types = catalog.observeTypes().first()
            assertEquals(3, types.size)
            val (printer, cartridge, chip) = types
            assertNull(printer.parentId)
            assertEquals(printer.id, cartridge.parentId)
            assertEquals(cartridge.id, chip.parentId)
            assertEquals(listOf(FieldKind.TEXT, FieldKind.CHOICE, FieldKind.CHOICE), printer.fields.map { it.kind })
            assertEquals(listOf(true, false, false), printer.fields.map { it.inTitle })

            val entries = catalog.observeCatalog().first()
            assertEquals(4, entries.size)
            val m125 = entries.first { it.item.name == "LaserJet M125" }
            assertEquals("HP LaserJet M125", m125.item.title)
            assertEquals(1, m125.ownedCount)
            assertEquals(listOf("CF283A"), m125.item.parts.map { it.title })
            val pixma = entries.first { it.item.name == "Pixma G3410" }.item
            assertEquals("Canon Pixma G3410", pixma.title)
            assertEquals(printer.fields[1].options[1], pixma.values[printer.fields[1].id])
            assertEquals(printer.fields[2].options[1], pixma.values[printer.fields[2].id])
            val cf283a = entries.first { it.item.name == "CF283A" }.item
            assertEquals(listOf("83A chip"), cf283a.parts.map { it.title })

            // The client's printer keeps its id, place, serial and sync id; its cartridge row too.
            val owned = clientItems.getClientItem(7)!!
            assertEquals("Office", owned.place)
            assertEquals("old one", owned.note)
            assertEquals("cnb 123", owned.serialNumber)
            assertEquals(listOf(9L), owned.parts.map { it.id })
            assertEquals(listOf("s7"), db.rows("SELECT sync_id FROM client_items"))
            assertEquals(1, clientItems.findBySerial("CNB123").size)

            // Work, photos, prices and expenses point the same way as before.
            assertEquals(listOf("1|7|9|ok"), db.rows("SELECT order_id, client_item_id, client_item_part_id, note FROM repairs"))
            assertEquals(listOf("1|1|2"), db.rows("SELECT repair_id, part_id, quantity FROM repair_items"))
            assertEquals(listOf("CLIENT_ITEM|7", "CLIENT_ITEM_PART|9", "ORDER|1"), db.rows("SELECT owner_type, owner_id FROM photos ORDER BY id"))
            assertEquals(listOf("Refill|${cartridge.id}", "Drum|${printer.id}", "Visit|null"), db.rows("SELECT name, type_id FROM repair_parts ORDER BY id"))
            assertEquals(listOf("MATERIALS", "GOODS"), db.rows("SELECT category FROM expenses ORDER BY id"))
            assertEquals(emptyList<String>(), db.rows("PRAGMA foreign_key_check"))
            // The old printer tables are gone.
            assertEquals(emptyList<String>(), db.rows("SELECT name FROM sqlite_master WHERE name IN ('brands', 'printer_models', 'client_printers', 'cartridges', 'chips')"))
        } finally {
            db.close()
        }
    }
}
