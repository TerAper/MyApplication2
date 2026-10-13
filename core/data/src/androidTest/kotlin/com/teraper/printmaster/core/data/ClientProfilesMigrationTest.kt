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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A version-14 database (item types, client items always of a catalog model) is opened by today's app.
 * assets/schema-14.json is a copy of core/database/schemas/…/14.json, the tables as they were then.
 */
@RunWith(AndroidJUnit4::class)
class ClientProfilesMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = context.getDatabasePath(PrintMasterDatabase.DATABASE_NAME)

    @Before
    fun setUp() {
        context.deleteDatabase(PrintMasterDatabase.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("schema-14.json").bufferedReader().use { JSONObject(it.readText()) }
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
            listOf(
                "INSERT INTO companies (id, name, bank_accounts, color_index, created_at) VALUES (1, 'Main', '', 0, 0)",
                "INSERT INTO clients (id, name, type, note, created_at) VALUES (1, 'Gamma', 'FIRM', '', 0)",
                "INSERT INTO item_types (id, name, parent_id) VALUES (1, 'Printer', NULL), (2, 'Cartridge', 1)",
                "INSERT INTO items (id, type_id, name) VALUES (1, 1, 'M125'), (2, 2, 'CF283A')",
                "INSERT INTO item_fits (item_id, into_id) VALUES (2, 1)",
                "INSERT INTO client_items (id, client_id, item_id, place, note, sync_id, serial_number, serial_key) VALUES (7, 1, 1, 'Office', '', 's7', 'SN1', 'SN1')",
                "INSERT INTO client_item_parts (id, client_item_id, item_id, note) VALUES (9, 7, 2, '')",
                "INSERT INTO orders (id, company_id, client_id, scheduled_at, description, status, created_at) VALUES (1, 1, 1, 0, 'Refill', 'DONE', 0)",
                "INSERT INTO repairs (id, order_id, client_item_id, client_item_part_id, note, created_at) VALUES (1, 1, 7, 9, '', 0)",
            ).forEach(db::execSQL)
            db.version = 14
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(PrintMasterDatabase.DATABASE_NAME)
    }

    private fun PrintMasterDatabase.rows(sql: String): List<String> = openHelper.readableDatabase.query(sql).use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
    }

    @Test
    fun clientItemsKeepTheirIdsAndGetTheirTypes() = runTest {
        val db = PrintMasterDatabase.create(context)
        try {
            val writer = CatalogWriter(db.catalogDao())
            val types = OfflineCatalogRepository(db, db.catalogDao(), writer).observeTypes().first()
            // Types keep having serials and stay catalog types.
            assertTrue(types.all { it.hasSerial && !it.perClient })

            val owned = OfflineClientItemsRepository(db, db.catalogDao(), writer).getClientItem(7)!!
            assertEquals("M125", owned.title)
            assertEquals("Office", owned.place)
            assertEquals("SN1", owned.serialNumber)
            assertEquals(listOf(9L to "CF283A"), owned.parts.map { it.id to it.title })
            assertEquals(listOf("7|1|1|s7"), db.rows("SELECT id, type_id, item_id, sync_id FROM client_items"))
            assertEquals(listOf("9|2|2"), db.rows("SELECT id, type_id, item_id FROM client_item_parts"))
            assertEquals(listOf("7|9"), db.rows("SELECT client_item_id, client_item_part_id FROM repairs"))
            assertEquals(emptyList<String>(), db.rows("PRAGMA foreign_key_check"))
        } finally {
            db.close()
        }
    }
}
