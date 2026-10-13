package com.teraper.printmaster.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 14 → 15: a type can have no serial or be one of a kind (a client's child, described on the client);
 * a client's item may have no catalog model, and its parts may be described by values instead.
 * Rows keep their ids, so work, photos and sync ids stay linked.
 */
internal object ClientProfilesMigration : Migration(14, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // The sync triggers name the tables rebuilt below; they are made again on every open.
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.forEach { db.execSQL("DROP TRIGGER IF EXISTS `$it`") }

        db.execSQL("ALTER TABLE item_types ADD COLUMN `has_serial` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE item_types ADD COLUMN `per_client` INTEGER NOT NULL DEFAULT 0")

        db.execSQL(CLIENT_ITEMS.replace("`client_items`", "`client_items_new`"))
        db.execSQL(
            """
            INSERT INTO client_items_new (id, client_id, type_id, item_id, name, place, note, sync_id, serial_number, serial_key)
            SELECT c.id, c.client_id, i.type_id, c.item_id, '', c.place, c.note, c.sync_id, c.serial_number, c.serial_key
            FROM client_items c JOIN items i ON i.id = c.item_id
            """.trimIndent(),
        )
        db.execSQL(PARTS.replace("`client_item_parts`", "`client_item_parts_new`"))
        db.execSQL(
            """
            INSERT INTO client_item_parts_new (id, client_item_id, type_id, item_id, note, serial_number)
            SELECT p.id, p.client_item_id, i.type_id, p.item_id, p.note, NULL
            FROM client_item_parts p JOIN items i ON i.id = p.item_id
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE client_item_parts")
        db.execSQL("DROP TABLE client_items")
        db.execSQL("ALTER TABLE client_items_new RENAME TO client_items")
        db.execSQL("ALTER TABLE client_item_parts_new RENAME TO client_item_parts")
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_client_items_client_id` ON `client_items` (`client_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_items_type_id` ON `client_items` (`type_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_items_item_id` ON `client_items` (`item_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_items_serial_key` ON `client_items` (`serial_key`)",
            "CREATE INDEX IF NOT EXISTS `index_client_item_parts_client_item_id` ON `client_item_parts` (`client_item_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_item_parts_type_id` ON `client_item_parts` (`type_id`)",
            "CREATE INDEX IF NOT EXISTS `index_client_item_parts_item_id` ON `client_item_parts` (`item_id`)",
            "CREATE TABLE IF NOT EXISTS `client_item_values` (`client_item_id` INTEGER NOT NULL, `field_id` INTEGER NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`client_item_id`, `field_id`), FOREIGN KEY(`client_item_id`) REFERENCES `client_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`field_id`) REFERENCES `item_fields`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_client_item_values_field_id` ON `client_item_values` (`field_id`)",
            "CREATE TABLE IF NOT EXISTS `client_item_part_values` (`part_id` INTEGER NOT NULL, `field_id` INTEGER NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`part_id`, `field_id`), FOREIGN KEY(`part_id`) REFERENCES `client_item_parts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`field_id`) REFERENCES `item_fields`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_client_item_part_values_field_id` ON `client_item_part_values` (`field_id`)",
        ).forEach(db::execSQL)
    }

    private const val CLIENT_ITEMS = "CREATE TABLE IF NOT EXISTS `client_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_id` INTEGER NOT NULL, `type_id` INTEGER NOT NULL, `item_id` INTEGER, `name` TEXT NOT NULL, `place` TEXT NOT NULL, `note` TEXT NOT NULL, `sync_id` TEXT, `serial_number` TEXT, `serial_key` TEXT, FOREIGN KEY(`client_id`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`type_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"

    private const val PARTS = "CREATE TABLE IF NOT EXISTS `client_item_parts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_item_id` INTEGER NOT NULL, `type_id` INTEGER NOT NULL, `item_id` INTEGER, `note` TEXT NOT NULL, `serial_number` TEXT, FOREIGN KEY(`client_item_id`) REFERENCES `client_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`type_id`) REFERENCES `item_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`item_id`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
}
