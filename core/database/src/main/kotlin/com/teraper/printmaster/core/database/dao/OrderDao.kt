package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

/** Orders with the names they show (client, address, phone, master). Always one company. */
@Dao
interface OrderDao {

    @Query("$SELECT_ROWS WHERE o.company_id = :companyId AND o.scheduled_at >= :fromMillis AND o.scheduled_at < :toMillis ORDER BY o.scheduled_at")
    fun observeOrdersBetween(companyId: Long, fromMillis: Long, toMillis: Long): Flow<List<OrderRow>>

    /** Unfinished orders planned before [beforeMillis]: the ones that were missed. */
    @Query(
        "$SELECT_ROWS WHERE o.company_id = :companyId AND o.scheduled_at < :beforeMillis " +
            "AND o.status IN ('NEW', 'IN_PROGRESS') ORDER BY o.scheduled_at",
    )
    fun observeOpenOrdersBefore(companyId: Long, beforeMillis: Long): Flow<List<OrderRow>>

    @Query("$SELECT_ROWS WHERE o.company_id = :companyId AND o.client_id = :clientId ORDER BY o.scheduled_at DESC")
    fun observeClientOrders(companyId: Long, clientId: Long): Flow<List<OrderRow>>

    @Query("$SELECT_ROWS WHERE o.id = :id")
    fun observeOrder(id: Long): Flow<OrderRow?>

    /** Not-cancelled order times in a range, for the per-day counts on the day strip. */
    @Query(
        "SELECT scheduled_at FROM orders WHERE company_id = :companyId " +
            "AND scheduled_at >= :fromMillis AND scheduled_at < :toMillis AND status != 'CANCELLED'",
    )
    fun observeOrderTimes(companyId: Long, fromMillis: Long, toMillis: Long): Flow<List<Long>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): OrderEntity?

    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: OrderStatus): Int

    /** Repairs and payments tied to the order; deleting would lose or orphan them. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM repairs WHERE order_id = :id)
             + (SELECT COUNT(*) FROM payments WHERE order_id = :id)
        """,
    )
    suspend fun countRecordsBlockingDelete(id: Long): Int

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: Long): Int

    private companion object {
        const val SELECT_ROWS = """
            SELECT o.*, c.name AS client_name, a.address AS address_text, p.number AS phone_number, m.name AS master_name
            FROM orders o
            JOIN clients c ON c.id = o.client_id
            LEFT JOIN client_addresses a ON a.id = o.address_id
            LEFT JOIN client_phones p ON p.id = o.phone_id
            LEFT JOIN masters m ON m.id = o.master_id
        """
    }
}

data class OrderRow(
    @Embedded val order: OrderEntity,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "address_text") val address: String?,
    @ColumnInfo(name = "phone_number") val phone: String?,
    @ColumnInfo(name = "master_name") val masterName: String?,
)
