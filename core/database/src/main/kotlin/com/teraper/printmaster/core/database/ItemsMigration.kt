package com.teraper.printmaster.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The words the old printer catalog turns into, in the app's language. */
data class PrinterWords(
    val printer: String = "Printer",
    val cartridge: String = "Cartridge",
    val chip: String = "Chip",
    val brand: String = "Brand",
    val printType: String = "Print type",
    val color: String = "Color",
    val laser: String = "Laser",
    val ink: String = "Inkjet",
    val mono: String = "Black/white",
    val colored: String = "Color",
) {
    companion object {
        fun from(context: Context) = PrinterWords(
            printer = context.getString(R.string.core_database_type_printer),
            cartridge = context.getString(R.string.core_database_type_cartridge),
            chip = context.getString(R.string.core_database_type_chip),
            brand = context.getString(R.string.core_database_field_brand),
            printType = context.getString(R.string.core_database_field_print),
            color = context.getString(R.string.core_database_field_color),
            laser = context.getString(R.string.core_database_print_laser),
            ink = context.getString(R.string.core_database_print_ink),
            mono = context.getString(R.string.core_database_color_mono),
            colored = context.getString(R.string.core_database_color_color),
        )
    }
}

/**
 * 13 → 14: the printer catalog becomes the customer's own item types. Brands, printer models,
 * cartridges and chips become items of the types Printer → Cartridge → Chip (brand, print type and
 * color become fields), client printers become client items with the same ids, so photos and work
 * stay linked, and price-list groups become types.
 */
internal class ItemsMigration(private val words: PrinterWords) : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // The sync triggers name the tables rebuilt below, and SQLite won't rename a table while a
        // trigger points at a missing one. They are made again on every open (SyncTriggers).
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }
        createTables(db)

        val hasPrinterData = count(db, "SELECT COUNT(*) FROM printer_models") +
            count(db, "SELECT COUNT(*) FROM cartridges") +
            count(db, "SELECT COUNT(*) FROM repair_parts WHERE category IN ('PRINTER', 'CARTRIDGE')") > 0

        var printerType = 0L
        var cartridgeType = 0L
        val modelItem = HashMap<Long, Long>()
        val cartridgeItem = HashMap<Long, Long>()
        if (hasPrinterData) {
            printerType = insert(db, "INSERT INTO item_types (name, parent_id) VALUES (?, NULL)", words.printer)
            cartridgeType = insert(db, "INSERT INTO item_types (name, parent_id) VALUES (?, ?)", words.cartridge, printerType)
            val chipType = insert(db, "INSERT INTO item_types (name, parent_id) VALUES (?, ?)", words.chip, cartridgeType)
            val brandField = field(db, printerType, words.brand, "TEXT", "", inTitle = true, position = 0)
            val printField = field(db, printerType, words.printType, "CHOICE", "${words.laser}\n${words.ink}", inTitle = false, position = 1)
            val colorField = field(db, printerType, words.color, "CHOICE", "${words.mono}\n${words.colored}", inTitle = false, position = 2)

            db.query("SELECT m.id, m.name, b.name, m.print_type, m.color_type FROM printer_models m JOIN brands b ON b.id = m.brand_id").use { c ->
                while (c.moveToNext()) {
                    val item = insert(db, "INSERT INTO items (type_id, name) VALUES (?, ?)", printerType, c.getString(1))
                    modelItem[c.getLong(0)] = item
                    value(db, item, brandField, c.getString(2))
                    value(db, item, printField, if (c.getString(3) == "INK") words.ink else words.laser)
                    value(db, item, colorField, if (c.getString(4) == "COLOR") words.colored else words.mono)
                }
            }
            db.query("SELECT id, name FROM cartridges").use { c ->
                while (c.moveToNext()) cartridgeItem[c.getLong(0)] = insert(db, "INSERT INTO items (type_id, name) VALUES (?, ?)", cartridgeType, c.getString(1))
            }
            val chipItem = HashMap<Long, Long>()
            db.query("SELECT id, name FROM chips").use { c ->
                while (c.moveToNext()) chipItem[c.getLong(0)] = insert(db, "INSERT INTO items (type_id, name) VALUES (?, ?)", chipType, c.getString(1))
            }
            db.query("SELECT cartridge_id, model_id FROM model_cartridges").use { c ->
                while (c.moveToNext()) fit(db, cartridgeItem[c.getLong(0)], modelItem[c.getLong(1)])
            }
            db.query("SELECT chip_id, cartridge_id FROM cartridge_chips").use { c ->
                while (c.moveToNext()) fit(db, chipItem[c.getLong(0)], cartridgeItem[c.getLong(1)])
            }
        }

        // Client printers keep their ids, so photos, work and sync ids stay theirs.
        db.query("SELECT id, client_id, model_id, location, note, sync_id, serial_number, serial_key FROM client_printers").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO client_items (id, client_id, item_id, place, note, sync_id, serial_number, serial_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(c.getLong(0), c.getLong(1), modelItem.getValue(c.getLong(2)), c.getString(3), c.getString(4), c.text(5), c.text(6), c.text(7)),
                )
            }
        }
        db.query("SELECT id, client_printer_id, cartridge_id, note FROM client_printer_cartridges").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO client_item_parts (id, client_item_id, item_id, note) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(c.getLong(0), c.getLong(1), cartridgeItem.getValue(c.getLong(2)), c.getString(3)),
                )
            }
        }

        // Work: same rows, now pointing at client items and their parts.
        db.execSQL(REPAIRS_SQL.replace("`repairs`", "`repairs_new`"))
        db.execSQL(
            """
            INSERT INTO repairs_new (id, order_id, client_item_id, client_item_part_id, note, created_at, sync_id)
            SELECT id, order_id, client_printer_id, client_printer_cartridge_id, note, created_at, sync_id FROM repairs
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE repairs")
        db.execSQL("ALTER TABLE repairs_new RENAME TO repairs")
        REPAIRS_INDICES.forEach(db::execSQL)

        // Price list: the Printer / Cartridge groups become those types; Other = general.
        db.execSQL(PARTS_SQL.replace("`repair_parts`", "`repair_parts_new`"))
        db.execSQL(
            """
            INSERT INTO repair_parts_new (id, type_id, name, description, price_minor, cost_minor, archived, sync_id, attached_company_id)
            SELECT id,
                   CASE category WHEN 'PRINTER' THEN ${printerType.orNull()} WHEN 'CARTRIDGE' THEN ${cartridgeType.orNull()} ELSE NULL END,
                   name, description, price_minor, cost_minor, archived, sync_id, attached_company_id
            FROM repair_parts
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE repair_parts")
        db.execSQL("ALTER TABLE repair_parts_new RENAME TO repair_parts")
        PARTS_INDICES.forEach(db::execSQL)

        // Expense groups named for printers become general ones.
        db.execSQL("UPDATE expenses SET category = 'GOODS' WHERE category = 'PARTS'")
        db.execSQL("UPDATE expenses SET category = 'MATERIALS' WHERE category = 'TONER'")

        db.execSQL("UPDATE photos SET owner_type = 'CLIENT_ITEM' WHERE owner_type = 'PRINTER'")
        db.execSQL("UPDATE photos SET owner_type = 'CLIENT_ITEM_PART' WHERE owner_type = 'CARTRIDGE'")

        listOf("client_printer_cartridges", "client_printers", "cartridge_chips", "model_cartridges", "chips", "cartridges", "printer_models", "brands")
            .forEach { db.execSQL("DROP TABLE $it") }
    }

    private fun createTables(db: SupportSQLiteDatabase) {
        listOf(
            "CREATE TABLE IF NOT EXISTS `item_types` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `parent_id` INTEGER, FOREIGN KEY(`parent_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_item_types_parent_id` ON `item_types` (`parent_id`)",
            "CREATE TABLE IF NOT EXISTS `item_fields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `options` TEXT NOT NULL, `in_title` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`type_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_item_fields_type_id` ON `item_fields` (`type_id`)",
            "CREATE TABLE IF NOT EXISTS `items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type_id` INTEGER NOT NULL, `name` TEXT NOT NULL, FOREIGN KEY(`type_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_items_type_id` ON `items` (`type_id`)",
            "CREATE TABLE IF NOT EXISTS `item_values` (`item_id` INTEGER NOT NULL, `field_id` INTEGER NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`item_id`, `field_id`), FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`field_id`) REFERENCES `item_fields`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_item_values_field_id` ON `item_values` (`field_id`)",
            "CREATE TABLE IF NOT EXISTS `item_fits` (`item_id` INTEGER NOT NULL, `into_id` INTEGER NOT NULL, PRIMARY KEY(`item_id`, `into_id`), FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`into_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_item_fits_into_id` ON `item_fits` (`into_id`)",
            "CREATE TABLE IF NOT EXISTS `client_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_id` INTEGER NOT NULL, `item_id` INTEGER NOT NULL, `place` TEXT NOT NULL, `note` TEXT NOT NULL, `sync_id` TEXT, `serial_number` TEXT, `serial_key` TEXT, FOREIGN KEY(`client_id`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_client_items_client_id` ON `client_items` (`client_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_items_item_id` ON `client_items` (`item_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_items_serial_key` ON `client_items` (`serial_key`)",
            "CREATE TABLE IF NOT EXISTS `client_item_parts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_item_id` INTEGER NOT NULL, `item_id` INTEGER NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`client_item_id`) REFERENCES `client_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_client_item_parts_client_item_id` ON `client_item_parts` (`client_item_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_item_parts_item_id` ON `client_item_parts` (`item_id`)",
        ).forEach(db::execSQL)
    }

    private fun field(db: SupportSQLiteDatabase, typeId: Long, name: String, kind: String, options: String, inTitle: Boolean, position: Int): Long =
        insert(db, "INSERT INTO item_fields (type_id, name, kind, options, in_title, position) VALUES (?, ?, ?, ?, ?, ?)", typeId, name, kind, options, if (inTitle) 1 else 0, position)

    private fun value(db: SupportSQLiteDatabase, itemId: Long, fieldId: Long, value: String) =
        db.execSQL("INSERT INTO item_values (item_id, field_id, value) VALUES (?, ?, ?)", arrayOf<Any>(itemId, fieldId, value))

    private fun fit(db: SupportSQLiteDatabase, itemId: Long?, intoId: Long?) {
        if (itemId != null && intoId != null) db.execSQL("INSERT OR IGNORE INTO item_fits (item_id, into_id) VALUES (?, ?)", arrayOf<Any>(itemId, intoId))
    }

    private fun insert(db: SupportSQLiteDatabase, sql: String, vararg args: Any): Long {
        db.execSQL(sql, args)
        return db.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
    }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private fun Cursor.text(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Long.orNull(): String = if (this == 0L) "NULL" else toString()

    private companion object {
        const val REPAIRS_SQL = "CREATE TABLE IF NOT EXISTS `repairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `order_id` INTEGER NOT NULL, `client_item_id` INTEGER, `client_item_part_id` INTEGER, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `sync_id` TEXT, FOREIGN KEY(`order_id`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`client_item_id`) REFERENCES `client_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`client_item_part_id`) REFERENCES `client_item_parts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        val REPAIRS_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_repairs_order_id` ON `repairs` (`order_id`)",
            "CREATE INDEX IF NOT EXISTS `index_repairs_client_item_id` ON `repairs` (`client_item_id`)",
            "CREATE INDEX IF NOT EXISTS `index_repairs_client_item_part_id` ON `repairs` (`client_item_part_id`)",
        )
        const val PARTS_SQL = "CREATE TABLE IF NOT EXISTS `repair_parts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type_id` INTEGER, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `price_minor` INTEGER NOT NULL, `cost_minor` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sync_id` TEXT, `attached_company_id` INTEGER, FOREIGN KEY(`type_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        val PARTS_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS `index_repair_parts_type_id` ON `repair_parts` (`type_id`)",
            "CREATE INDEX IF NOT EXISTS `index_repair_parts_attached_company_id` ON `repair_parts` (`attached_company_id`)",
        )
    }
}
