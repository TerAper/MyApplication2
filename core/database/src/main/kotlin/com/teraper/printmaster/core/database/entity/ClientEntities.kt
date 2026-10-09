package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.ClientType

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["tax_id"], unique = true),
        Index(value = ["name"]),
        Index(value = ["sync_id"], unique = true),
    ],
)
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ClientType,
    /** ՀՎՀՀ. Null for private clients; unique when present (used to match imported invoices). */
    @ColumnInfo(name = "tax_id") val taxId: String?,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Same client on every phone of the company (see sync). Null until first shared. */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
    /** Added by a master in the field: the company should check it. */
    @ColumnInfo(name = "needs_review", defaultValue = "0") val needsReview: Boolean = false,
)

@Entity(
    tableName = "client_phones",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["client_id"])],
)
data class ClientPhoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    val number: String,
    val label: String = "",
)

@Entity(
    tableName = "client_addresses",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["client_id"])],
)
data class ClientAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    val address: String,
    val label: String = "",
    /** The exact point ("geo:lat,lon…") or a map link, picked on a map; null = only the text. */
    @ColumnInfo(name = "map_link") val mapLink: String? = null,
)

/**
 * A spelling of the client's name as it appears in bank statements. Once the user
 * confirms a match, later imports with the same normalized name go to this client.
 */
@Entity(
    tableName = "client_aliases",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["normalized_name"], unique = true),
    ],
)
data class ClientAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "raw_name") val rawName: String,
)
