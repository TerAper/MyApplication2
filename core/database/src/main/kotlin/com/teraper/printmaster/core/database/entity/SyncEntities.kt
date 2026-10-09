package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A row that changed on this phone and should be sent to the shared space. Filled by
 * database triggers (see SyncTriggers), so no screen or repository has to remember it.
 * [entity]: "client", "order" or "price".
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["entity", "row_id"])
data class SyncOutboxEntity(
    val entity: String,
    @ColumnInfo(name = "row_id") val rowId: Long,
)

/** Small key/value settings of the sync: last pull time, "applying remote changes" flag. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)
