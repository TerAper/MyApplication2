package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import kotlinx.coroutines.flow.Flow

/** Work done on orders, and the charges / cash that finishing an order creates. */
@Dao
interface RepairDao {

    @Transaction
    @Query("SELECT * FROM repairs WHERE order_id = :orderId ORDER BY id")
    fun observeRepairs(orderId: Long): Flow<List<RepairWithItems>>

    @Transaction
    @Query("SELECT * FROM repairs WHERE id = :id")
    fun observeRepair(id: Long): Flow<RepairWithItems?>

    @Transaction
    @Query("SELECT * FROM repairs WHERE order_id = :orderId ORDER BY id")
    suspend fun getRepairs(orderId: Long): List<RepairWithItems>

    @Query("SELECT * FROM repairs WHERE id = :id")
    suspend fun getRepair(id: Long): RepairEntity?

    @Insert
    suspend fun insertRepair(repair: RepairEntity): Long

    @Update
    suspend fun updateRepair(repair: RepairEntity)

    @Insert
    suspend fun insertItems(items: List<RepairItemEntity>)

    @Query("DELETE FROM repair_items WHERE repair_id = :repairId")
    suspend fun deleteItems(repairId: Long)

    @Query("DELETE FROM repairs WHERE id = :id")
    suspend fun deleteRepair(id: Long): Int

    /** Charges made for this order's repairs; more than zero = the order was billed. */
    @Query("SELECT COUNT(*) FROM charges WHERE repair_id IN (SELECT id FROM repairs WHERE order_id = :orderId)")
    fun observeChargeCount(orderId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM charges WHERE repair_id IN (SELECT id FROM repairs WHERE order_id = :orderId)")
    suspend fun countCharges(orderId: Long): Int

    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM payments WHERE order_id = :orderId AND method = 'CASH'")
    fun observeCashPaid(orderId: Long): Flow<Long>

    /** Undoes finishing: the client no longer owes for the work. */
    @Query("DELETE FROM charges WHERE source = 'REPAIR' AND repair_id IN (SELECT id FROM repairs WHERE order_id = :orderId)")
    suspend fun deleteCharges(orderId: Long)

    @Query("DELETE FROM payments WHERE order_id = :orderId AND method = 'CASH'")
    suspend fun deleteCashPayments(orderId: Long)
}

data class RepairWithItems(
    @Embedded val repair: RepairEntity,
    @Relation(parentColumn = "id", entityColumn = "repair_id")
    val items: List<RepairItemEntity>,
)
