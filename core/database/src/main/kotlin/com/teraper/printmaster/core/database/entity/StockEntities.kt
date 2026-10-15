package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.AdjustReason
import com.teraper.printmaster.core.model.StockKind

/**
 * One stock movement of a catalog item; the count is the sum of [quantity]. [companyId] is the
 * company whose warehouse it is in, null = the one shared warehouse.
 */
@Entity(
    tableName = "stock_moves",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["item_id"]), Index(value = ["company_id"]), Index(value = ["order_id"])],
)
data class StockMoveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    @ColumnInfo(name = "company_id") val companyId: Long?,
    val quantity: Int,
    val kind: StockKind,
    val at: Long,
    @ColumnInfo(name = "unit_cost_minor") val unitCostMinor: Long = 0,
    val reason: AdjustReason? = null,
    val note: String = "",
    @ColumnInfo(name = "order_id") val orderId: Long? = null,
    /** The other company of a move. */
    @ColumnInfo(name = "other_company_id") val otherCompanyId: Long? = null,
)

/** On a master's phone: an attached company's stock of one item, as it shares it ([docId] is its id there). */
@Entity(
    tableName = "attached_stock",
    primaryKeys = ["attached_company_id", "doc_id"],
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["attached_company_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AttachedStockEntity(
    @ColumnInfo(name = "attached_company_id") val attachedCompanyId: Long,
    @ColumnInfo(name = "doc_id") val docId: String,
    val type: String,
    val name: String,
    val count: Int,
    /** Null = the company doesn't show prices. */
    @ColumnInfo(name = "price_minor") val priceMinor: Long? = null,
)
