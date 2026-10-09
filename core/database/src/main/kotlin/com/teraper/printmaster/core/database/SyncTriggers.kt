package com.teraper.printmaster.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every change to a client, an order (and its work) or a price-list item lands in
 * sync_outbox, so the sync sends it later, even after the app was closed with no internet.
 * Changes written by the sync itself (sync_state "applying" = 1) are not sent back.
 */
internal object SyncTriggers {

    private const val NOT_APPLYING = "COALESCE((SELECT value FROM sync_state WHERE key = 'applying'), '0') != '1'"

    /** (trigger name, table, events, entity, row id expression with NEW/OLD). */
    private val TRIGGERS = listOf(
        Trigger("clients", listOf("INSERT", "UPDATE"), "client", "NEW.id"),
        Trigger("client_phones", listOf("INSERT", "UPDATE"), "client", "NEW.client_id"),
        Trigger("client_phones", listOf("DELETE"), "client", "OLD.client_id"),
        Trigger("client_addresses", listOf("INSERT", "UPDATE"), "client", "NEW.client_id"),
        Trigger("client_addresses", listOf("DELETE"), "client", "OLD.client_id"),
        Trigger("client_printers", listOf("INSERT", "UPDATE"), "client", "NEW.client_id"),
        Trigger("client_printers", listOf("DELETE"), "client", "OLD.client_id"),
        Trigger("orders", listOf("INSERT", "UPDATE"), "order", "NEW.id"),
        Trigger("repairs", listOf("INSERT", "UPDATE"), "order", "NEW.order_id"),
        Trigger("repairs", listOf("DELETE"), "order", "OLD.order_id"),
        Trigger("repair_items", listOf("INSERT", "UPDATE"), "order", "(SELECT order_id FROM repairs WHERE id = NEW.repair_id)"),
        Trigger("repair_items", listOf("DELETE"), "order", "(SELECT order_id FROM repairs WHERE id = OLD.repair_id)"),
        Trigger("repair_parts", listOf("INSERT", "UPDATE"), "price", "NEW.id"),
    )

    private data class Trigger(val table: String, val events: List<String>, val entity: String, val rowId: String)

    fun create(db: SupportSQLiteDatabase) {
        TRIGGERS.forEach { t ->
            t.events.forEach { event ->
                val name = "sync_${t.table}_${event.lowercase()}"
                // Recreated on every open, so a fixed trigger replaces an older one.
                db.execSQL("DROP TRIGGER IF EXISTS $name")
                // Not "INSERT OR IGNORE": inside a trigger SQLite uses the outer statement's
                // conflict rule (Room inserts with OR ABORT), so duplicates are skipped by hand.
                db.execSQL(
                    """
                    CREATE TRIGGER $name AFTER $event ON ${t.table}
                    WHEN $NOT_APPLYING
                    BEGIN
                        INSERT INTO sync_outbox (entity, row_id) SELECT '${t.entity}', ${t.rowId}
                        WHERE ${t.rowId} IS NOT NULL
                          AND NOT EXISTS (SELECT 1 FROM sync_outbox WHERE entity = '${t.entity}' AND row_id = ${t.rowId});
                    END
                    """.trimIndent(),
                )
            }
        }
    }
}
