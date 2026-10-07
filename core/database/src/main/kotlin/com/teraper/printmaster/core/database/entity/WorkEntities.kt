package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.RepairCategory

/** A planned visit to a client. */
@Entity(
    tableName = "orders",
    foreignKeys = [
        // A client with orders can't be deleted by accident.
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ClientAddressEntity::class,
            parentColumns = ["id"],
            childColumns = ["address_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientPhoneEntity::class,
            parentColumns = ["id"],
            childColumns = ["phone_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["client_id"]),
        Index(value = ["scheduled_at"]),
        Index(value = ["address_id"]),
        Index(value = ["phone_id"]),
    ],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    /** Epoch millis of the planned visit, in the phone's time zone when shown. */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "address_id") val addressId: Long?,
    @ColumnInfo(name = "phone_id") val phoneId: Long?,
    val description: String,
    val status: OrderStatus = OrderStatus.NEW,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** One line of the price list. */
@Entity(tableName = "repair_parts", indices = [Index(value = ["category"])])
data class RepairPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: RepairCategory,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "cost_minor") val costMinor: Long,
    /** Hidden from the price list but kept so old repairs still make sense. */
    val archived: Boolean = false,
)

/** The work done during a visit on one device. */
@Entity(
    tableName = "repairs",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClientPrinterEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_printer_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientPrinterCartridgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_printer_cartridge_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["client_printer_id"]),
        Index(value = ["client_printer_cartridge_id"]),
    ],
)
data class RepairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "client_printer_id") val clientPrinterId: Long?,
    @ColumnInfo(name = "client_printer_cartridge_id") val clientPrinterCartridgeId: Long?,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * One price-list item used in a repair. Name, price and cost are copied at the time
 * of the repair, so later price-list changes don't rewrite history.
 */
@Entity(
    tableName = "repair_items",
    foreignKeys = [
        ForeignKey(
            entity = RepairEntity::class,
            parentColumns = ["id"],
            childColumns = ["repair_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RepairPartEntity::class,
            parentColumns = ["id"],
            childColumns = ["part_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["repair_id"]), Index(value = ["part_id"])],
)
data class RepairItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "repair_id") val repairId: Long,
    @ColumnInfo(name = "part_id") val partId: Long?,
    val name: String,
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "cost_minor") val costMinor: Long,
    val quantity: Int,
)
