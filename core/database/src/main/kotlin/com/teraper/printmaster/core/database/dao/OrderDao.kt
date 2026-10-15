package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

/** Orders with the names they show (client, address, phone, master). Always one company. */
@Dao
interface OrderDao {

    @Query("$SELECT_ROWS WHERE $COMPANY_OR_ATTACHED AND o.scheduled_at >= :fromMillis AND o.scheduled_at < :toMillis ORDER BY o.scheduled_at")
    fun observeOrdersBetween(companyId: Long, fromMillis: Long, toMillis: Long): Flow<List<OrderRow>>

    /** Unfinished orders planned before [beforeMillis]: the ones that were missed. */
    @Query(
        "$SELECT_ROWS WHERE $COMPANY_OR_ATTACHED AND o.scheduled_at < :beforeMillis " +
            "AND o.status IN ('NEW', 'IN_PROGRESS') ORDER BY o.scheduled_at",
    )
    fun observeOpenOrdersBefore(companyId: Long, beforeMillis: Long): Flow<List<OrderRow>>

    @Query("$SELECT_ROWS WHERE o.client_id = :clientId AND (o.company_id = :companyId OR c.attached_company_id IS NOT NULL) ORDER BY o.scheduled_at DESC")
    fun observeClientOrders(companyId: Long, clientId: Long): Flow<List<OrderRow>>

    @Query("$SELECT_ROWS WHERE o.id = :id")
    fun observeOrder(id: Long): Flow<OrderRow?>

    /** Orders with work on this client item (any company: it's the item's history). */
    @Query("$SELECT_ROWS WHERE o.id IN (SELECT order_id FROM repairs WHERE client_item_id = :clientItemId) ORDER BY o.scheduled_at DESC")
    fun observeClientItemOrders(clientItemId: Long): Flow<List<OrderRow>>

    /** Not-cancelled order times in a range, for the per-day counts on the day strip. */
    @Query(
        "SELECT scheduled_at FROM orders o WHERE $COMPANY_OR_ATTACHED " +
            "AND scheduled_at >= :fromMillis AND scheduled_at < :toMillis AND status != 'CANCELLED'",
    )
    fun observeOrderTimes(companyId: Long, fromMillis: Long, toMillis: Long): Flow<List<Long>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): OrderEntity?

    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    /** [doneAt]: when it was finished (epoch millis) for DONE, null otherwise. */
    @Query("UPDATE orders SET status = :status, done_at = :doneAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: OrderStatus, doneAt: Long? = null): Int

    /**
     * Money tied to the order: charges for its repairs and payments. Deleting would lose them.
     * Unbilled repairs don't count; they are deleted with the order.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM charges WHERE repair_id IN (SELECT id FROM repairs WHERE order_id = :id))
             + (SELECT COUNT(*) FROM payments WHERE order_id = :id)
        """,
    )
    suspend fun countRecordsBlockingDelete(id: Long): Int

    @Query("SELECT member_uid IS NOT NULL FROM masters WHERE id = :masterId")
    suspend fun isAttachedMaster(masterId: Long): Boolean

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long): Int

    /**
     * Master's phone: orders of attached companies he agreed to share his location on and still
     * does (accepted, open, not given to him as his own).
     */
    @Query(
        """
        SELECT o.id AS order_id, o.sync_id AS order_sync_id, c.space_id AS space_id FROM orders o
        JOIN companies c ON c.id = o.company_id
        WHERE c.kind = 'ATTACHED' AND c.space_id IS NOT NULL AND o.sync_id IS NOT NULL
          AND o.ask_location = 1 AND o.sharing_location = 1 AND o.accepted_at IS NOT NULL
          AND o.handed_over = 0 AND o.declined_reason IS NULL AND o.status IN ('NEW', 'IN_PROGRESS')
        """,
    )
    fun observeLocationShares(): Flow<List<TrackRef>>

    @Query(
        """
        SELECT o.id AS order_id, o.sync_id AS order_sync_id, c.space_id AS space_id FROM orders o
        JOIN companies c ON c.id = o.company_id
        WHERE c.kind = 'ATTACHED' AND c.space_id IS NOT NULL AND o.sync_id IS NOT NULL
          AND o.ask_location = 1 AND o.sharing_location = 1 AND o.accepted_at IS NOT NULL
          AND o.handed_over = 0 AND o.declined_reason IS NULL AND o.status IN ('NEW', 'IN_PROGRESS')
        """,
    )
    suspend fun getLocationShares(): List<TrackRef>

    /** Where an order's shared location lives: its space and its id there. */
    @Query(
        """
        SELECT o.id AS order_id, o.sync_id AS order_sync_id, c.space_id AS space_id FROM orders o
        JOIN companies c ON c.id = o.company_id
        WHERE o.id = :orderId AND o.sync_id IS NOT NULL AND c.space_id IS NOT NULL
        """,
    )
    suspend fun getTrackRef(orderId: Long): TrackRef?

    @Query("UPDATE orders SET sharing_location = :sharing WHERE id = :id")
    suspend fun setSharingLocation(id: Long, sharing: Boolean)

    private companion object {
        /** A master's day holds his own company's orders and those attached companies gave him. */
        const val COMPANY_OR_ATTACHED = "(o.company_id = :companyId OR o.company_id IN (SELECT id FROM companies WHERE kind = 'ATTACHED'))"

        const val SELECT_ROWS = """
            SELECT o.*, c.name AS client_name, a.address AS address_text, a.map_link AS address_link, p.number AS phone_number, m.name AS master_name,
                   co.kind AS company_kind, co.name AS company_name, co.owner_name AS company_owner,
                   (SELECT name FROM companies WHERE id = o.from_company_id) AS from_company_name,
                   m.member_uid IS NOT NULL AS master_attached
            FROM orders o
            JOIN clients c ON c.id = o.client_id
            JOIN companies co ON co.id = o.company_id
            LEFT JOIN client_addresses a ON a.id = o.address_id
            LEFT JOIN client_phones p ON p.id = o.phone_id
            LEFT JOIN masters m ON m.id = o.master_id
        """
    }
}

/** An order's shared location: document [orderSyncId] in space [spaceId]. */
data class TrackRef(
    @ColumnInfo(name = "order_id") val orderId: Long,
    @ColumnInfo(name = "order_sync_id") val orderSyncId: String,
    @ColumnInfo(name = "space_id") val spaceId: String,
)

data class OrderRow(
    @Embedded val order: OrderEntity,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "address_text") val address: String?,
    @ColumnInfo(name = "address_link") val addressLink: String?,
    @ColumnInfo(name = "phone_number") val phone: String?,
    @ColumnInfo(name = "master_name") val masterName: String?,
    @ColumnInfo(name = "company_kind") val companyKind: CompanyKind,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "company_owner") val companyOwner: String,
    @ColumnInfo(name = "from_company_name") val fromCompanyName: String?,
    @ColumnInfo(name = "master_attached") val masterAttached: Boolean,
)
