package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.OrderStatus

/** A planned visit to a client. */
@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["company_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MasterEntity::class,
            parentColumns = ["id"],
            childColumns = ["master_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
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
        Index(value = ["company_id"]),
        Index(value = ["master_id"]),
        Index(value = ["scheduled_at"]),
        Index(value = ["address_id"]),
        Index(value = ["phone_id"]),
    ],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "company_id") val companyId: Long,
    /** Who does the job; null = not assigned. */
    @ColumnInfo(name = "master_id") val masterId: Long? = null,
    @ColumnInfo(name = "client_id") val clientId: Long,
    /** Epoch millis of the planned visit, in the phone's time zone when shown. */
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "address_id") val addressId: Long?,
    @ColumnInfo(name = "phone_id") val phoneId: Long?,
    val description: String,
    val status: OrderStatus = OrderStatus.NEW,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Epoch millis when the order was finished (on whichever phone); null while open. */
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    /** Same order on every phone of the company (see sync). Null until first shared. */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
    /** Attached master's phone: the attached company this order came from (kept if moved to an own company). */
    @ColumnInfo(name = "from_company_id") val fromCompanyId: Long? = null,
    /** The attached master counts this order as his own: for the giving company it is only history. */
    @ColumnInfo(name = "taken_by_master", defaultValue = "0") val takenByMaster: Boolean = false,
    /** Owner's phone: the attached master who turned the order down, and why. */
    @ColumnInfo(name = "declined_by") val declinedBy: String? = null,
    @ColumnInfo(name = "declined_reason") val declinedReason: String? = null,
    /** Attached master's phone: the client paid cash for an order done for the attached company (no money is kept here). */
    @ColumnInfo(name = "paid_in_cash", defaultValue = "0") val paidInCash: Boolean = false,
    /**
     * Given to the attached master: his price, his money, his client; for the company only history.
     * False with an attached master = he works for the company, which charges the client.
     */
    @ColumnInfo(name = "handed_over", defaultValue = "0") val handedOver: Boolean = false,
    /** When the attached master accepted it; null = not yet (or he turned it down). */
    @ColumnInfo(name = "accepted_at") val acceptedAt: Long? = null,
)

/** One line of the price list; [typeId] is the item type it's work on, null = general. */
@Entity(
    tableName = "repair_parts",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["type_id"]), Index(value = ["attached_company_id"])],
)
data class RepairPartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type_id") val typeId: Long?,
    val name: String,
    val description: String = "",
    @ColumnInfo(name = "price_minor") val priceMinor: Long,
    @ColumnInfo(name = "cost_minor") val costMinor: Long,
    /** Hidden from the price list but kept so old repairs still make sense. */
    val archived: Boolean = false,
    /** Same item on every phone of the company (see sync). */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
    /** An attached company's price list item (for its orders); null = the user's own price list. */
    @ColumnInfo(name = "attached_company_id") val attachedCompanyId: Long? = null,
)

/** The work done during a visit on one client item (or none). */
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
            entity = ClientItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ClientItemPartEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_item_part_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["client_item_id"]),
        Index(value = ["client_item_part_id"]),
    ],
)
data class RepairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "client_item_id") val clientItemId: Long?,
    @ColumnInfo(name = "client_item_part_id") val clientItemPartId: Long?,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Same repair on every phone of the company (see sync). */
    @ColumnInfo(name = "sync_id") val syncId: String? = null,
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
