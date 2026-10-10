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

    /** Older phones had one shared space: everything there came from the company it joined. */
    @Query("UPDATE clients SET attached_company_id = :companyId WHERE attached_company_id IS NULL")
    suspend fun attachAllClients(companyId: Long)

    @Query("UPDATE repair_parts SET attached_company_id = :companyId WHERE attached_company_id IS NULL")
    suspend fun attachAllParts(companyId: Long)

    @Query(
        "UPDATE repair_parts SET category = :category, name = :name, description = :description, price_minor = :priceMinor, archived = :archived WHERE id = :id",
    )
    suspend fun updatePart(id: Long, category: String, name: String, description: String, priceMinor: Long, archived: Boolean)

    // Masters

    @Query("SELECT * FROM masters WHERE member_uid = :uid")
    suspend fun getMasterByUid(uid: String): MasterEntity?

    @Query("SELECT * FROM masters WHERE id = :id")
    suspend fun getMaster(id: Long): MasterEntity?

    /**
     * Attached masters of company [companyId] with an order of this client: the only ones who may
     * see the client in that company's space.
     */
    @Query(
        """
        SELECT DISTINCT m.member_uid FROM orders o
        JOIN masters m ON m.id = o.master_id
        JOIN company_members cm ON cm.master_id = m.id AND cm.company_id = o.company_id
        WHERE o.client_id = :clientId AND o.company_id = :companyId AND m.member_uid IS NOT NULL
        """,
    )
    suspend fun getMemberUidsOfClient(clientId: Long, companyId: Long): List<String>

    /** Is master [masterId] attached to company [companyId] (so its orders may go to him)? */
    @Query("SELECT COUNT(*) FROM company_members WHERE company_id = :companyId AND master_id = :masterId")
    suspend fun isMember(companyId: Long, masterId: Long): Int

    /** Own companies with a shared space where this client has an order of an attached master. */
    @Query(
        """
        SELECT DISTINCT o.company_id FROM orders o
        JOIN company_members cm ON cm.master_id = o.master_id AND cm.company_id = o.company_id
        WHERE o.client_id = :clientId
        """,
    )
    suspend fun getCompaniesSharingClient(clientId: Long): List<Long>

    @Query("UPDATE clients SET attached_company_id = :companyId WHERE id = :id")
    suspend fun setClientAttachedCompany(id: Long, companyId: Long?)

    @Query("SELECT id FROM client_printers WHERE client_id = :clientId")
    suspend fun getClientPrinterIds(clientId: Long): List<Long>

    @Query("UPDATE client_printers SET sync_id = :syncId WHERE id = :id")
    suspend fun setClientPrinterSyncId(id: Long, syncId: String)

    @Query("UPDATE client_printers SET location = :location WHERE id = :id")
    suspend fun setClientPrinterLocation(id: Long, location: String)

    @Query("DELETE FROM client_printers WHERE client_id = :clientId")
    suspend fun deleteClientPrinters(clientId: Long)

    @Query("DELETE FROM repairs WHERE order_id = :orderId")
    suspend fun deleteRepairsOf(orderId: Long)

    /** Owner: an attached master turned the order down; it waits for someone else. */
    @Query("UPDATE orders SET master_id = NULL, status = 'NEW', declined_by = :masterName, declined_reason = :reason WHERE id = :id")
    suspend fun declineOrder(id: Long, masterName: String, reason: String)

    /** Owner: the attached master did it as his own order; here it's only history. */
    @Query("UPDATE orders SET status = 'DONE', done_at = :doneAt, taken_by_master = 1 WHERE id = :id")
    suspend fun markTakenByMaster(id: Long, doneAt: Long)

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
