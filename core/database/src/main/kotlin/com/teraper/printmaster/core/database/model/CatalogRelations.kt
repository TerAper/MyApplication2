package com.teraper.printmaster.core.database.model

import androidx.room.ColumnInfo

/** A count per id, e.g. client items per catalog item. */
data class IdCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "count") val count: Int,
)

data class ItemOwnerRow(
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "place") val place: String,
)
