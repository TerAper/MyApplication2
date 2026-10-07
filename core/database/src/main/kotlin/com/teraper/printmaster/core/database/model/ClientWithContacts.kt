package com.teraper.printmaster.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity

data class ClientWithContacts(
    @Embedded val client: ClientEntity,
    @Relation(parentColumn = "id", entityColumn = "client_id")
    val phones: List<ClientPhoneEntity>,
    @Relation(parentColumn = "id", entityColumn = "client_id")
    val addresses: List<ClientAddressEntity>,
)

/** A per-client number from a GROUP BY query (sum of money, count of printers…). */
data class ClientTotal(
    @ColumnInfo(name = "client_id") val clientId: Long,
    val total: Long,
)
