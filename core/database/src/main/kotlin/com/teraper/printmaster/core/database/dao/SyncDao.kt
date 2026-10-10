package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.teraper.printmaster.core.database.model.ClientWithContacts
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.MasterEntity
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.database.entity.SyncOutboxEntity
import com.teraper.printmaster.core.database.entity.SyncStateEntity

/** Lookups and writes the sync needs; everything else goes through the regular DAOs. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_outbox")
    suspend fun getOutbox(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeOutboxCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM sync_outbox WHERE entity = :entity AND row_id = :rowId")
    suspend fun deleteOutbox(entity: String, rowId: Long)

    @Query("SELECT value FROM sync_state WHERE key = :key")
    suspend fun getState(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: SyncStateEntity)

    // Ids shared between phones

    @Query("SELECT * FROM clients WHERE sync_id = :syncId")
    suspend fun getClientBySyncId(syncId: String): ClientEntity?

    @Query("UPDATE clients SET sync_id = :syncId WHERE id = :id")
    suspend fun setClientSyncId(id: Long, syncId: String)

    @Query("SELECT * FROM orders WHERE sync_id = :syncId")
    suspend fun getOrderBySyncId(syncId: String): OrderEntity?

    @Query("UPDATE orders SET sync_id = :syncId WHERE id = :id")
    suspend fun setOrderSyncId(id: Long, syncId: String)

    @Query("UPDATE repairs SET sync_id = :syncId WHERE id = :id")
    suspend fun setRepairSyncId(id: Long, syncId: String)

    @Query("SELECT * FROM repair_parts WHERE sync_id = :syncId")
    suspend fun getPartBySyncId(syncId: String): RepairPartEntity?

    @Query("UPDATE repair_parts SET sync_id = :syncId WHERE id = :id")
    suspend fun setPartSyncId(id: Long, syncId: String)

    @Query("SELECT * FROM repair_parts")
    suspend fun getAllParts(): List<RepairPartEntity>

    @Insert
    suspend fun insertPart(part: RepairPartEntity): Long

    @Query(
        "UPDATE repair_parts SET category = :category, name = :name, description = :description, price_minor = :priceMinor, archived = :archived WHERE id = :id",
    )
    suspend fun updatePart(id: Long, category: String, name: String, description: String, priceMinor: Long, archived: Boolean)

    // Masters

    @Query("SELECT * FROM masters WHERE member_uid = :uid")
    suspend fun getMasterByUid(uid: String): MasterEntity?

    @Query("SELECT * FROM masters WHERE id = :id")
    suspend fun getMaster(id: Long): MasterEntity?

    /** Clients with an order assigned to a master who joined: the only ones masters may see. */
    @Query(
        """
        SELECT COUNT(*) FROM orders o JOIN masters m ON m.id = o.master_id
        WHERE o.client_id = :clientId AND m.member_uid IS NOT NULL
        """,
    )
    suspend fun countSharedOrders(clientId: Long): Int

    @Query("SELECT DISTINCT m.member_uid FROM orders o JOIN masters m ON m.id = o.master_id WHERE o.client_id = :clientId AND m.member_uid IS NOT NULL")
    suspend fun getMemberUidsOfClient(clientId: Long): List<String>

    @Query("SELECT id FROM client_printers WHERE client_id = :clientId")
    suspend fun getClientPrinterIds(clientId: Long): List<Long>

    @Query("DELETE FROM client_printers WHERE client_id = :clientId")
    suspend fun deleteClientPrinters(clientId: Long)

    @Query("DELETE FROM repairs WHERE order_id = :orderId")
    suspend fun deleteRepairsOf(orderId: Long)

    @Query("UPDATE orders SET status = :status, done_at = :doneAt, scheduled_at = :scheduledAt, description = :description WHERE id = :id")
    suspend fun updateOrderFields(id: Long, status: String, doneAt: Long?, scheduledAt: Long, description: String)

    @Transaction
    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getClientWithContacts(id: Long): ClientWithContacts?

    @Query("SELECT address FROM client_addresses WHERE id = :id")
    suspend fun getAddressText(id: Long): String?

    @Query("SELECT number FROM client_phones WHERE id = :id")
    suspend fun getPhoneText(id: Long): String?

    @Query("SELECT COUNT(*) FROM payments WHERE order_id = :orderId AND method = 'CASH'")
    suspend fun countCashPayments(orderId: Long): Int

    @Query("UPDATE clients SET needs_review = :needsReview WHERE id = :id")
    suspend fun setNeedsReview(id: Long, needsReview: Boolean)
}
