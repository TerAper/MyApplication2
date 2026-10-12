package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.FieldKind

/** A kind of thing the customer works with ("Printer", "Shirt"); [parentId] is the type it goes into. */
@Entity(
    tableName = "item_types",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["parent_id"])],
)
data class ItemTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
)

/** One field of a type; [options] are a choice field's values, one per line. */
@Entity(
    tableName = "item_fields",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["type_id"])],
)
data class ItemFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type_id") val typeId: Long,
    val name: String,
    val kind: FieldKind,
    val options: String = "",
    @ColumnInfo(name = "in_title") val inTitle: Boolean = false,
    /** Order of the fields in the form. */
    val position: Int = 0,
)

/** A real model in the catalog, e.g. a printer "LaserJet M125" or a cartridge "CF283A". */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["type_id"])],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type_id") val typeId: Long,
    val name: String,
)

/** The value of one field of one item. */
@Entity(
    tableName = "item_values",
    primaryKeys = ["item_id", "field_id"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ItemFieldEntity::class,
            parentColumns = ["id"],
            childColumns = ["field_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["field_id"])],
)
data class ItemValueEntity(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "field_id") val fieldId: Long,
    val value: String,
)

/** Item [itemId] goes into item [intoId] (a cartridge into the printer models it fits). */
@Entity(
    tableName = "item_fits",
    primaryKeys = ["item_id", "into_id"],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["into_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["into_id"])],
)
data class ItemFitEntity(
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "into_id") val intoId: Long,
)

/** Something a client owns, of a catalog model. */
@Entity(
    tableName = "client_items",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["client_id"]), Index(value = ["item_id"]), Index(value = ["serial_key"])],
)
data class ClientItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    /** Where it stands, e.g. "Accounting room". */
    val place: String = "",
    val note: String = "",
    /** Same item on every phone of the company; a client can have two of one model. */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
    /** The serial number as typed or scanned from the label; null = not known. */
    @ColumnInfo(name = "serial_number") val serialNumber: String? = null,
    /** [serialNumber] for searching: letters and digits only, upper case (see SerialNumbers.key). */
    @ColumnInfo(name = "serial_key") val serialKey: String? = null,
)

/** A part one of the client's items uses (a cartridge of their printer). */
@Entity(
    tableName = "client_item_parts",
    foreignKeys = [
        ForeignKey(
            entity = ClientItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["client_item_id"]), Index(value = ["item_id"])],
)
data class ClientItemPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_item_id") val clientItemId: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val note: String = "",
)
