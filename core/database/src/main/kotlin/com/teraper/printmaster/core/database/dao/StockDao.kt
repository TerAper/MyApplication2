package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.teraper.printmaster.core.database.entity.AttachedStockEntity
import com.teraper.printmaster.core.database.entity.StockMoveEntity
import com.teraper.printmaster.core.database.model.IdCount
import kotlinx.coroutines.flow.Flow

/** Stock movements; counts are their sums, per warehouse ([companyId] null = the shared one). */
@Dao
interface StockDao {

    @Query("SELECT item_id AS id, SUM(quantity) AS count FROM stock_moves WHERE company_id IS :companyId GROUP BY item_id")
    fun observeCounts(companyId: Long?): Flow<List<IdCount>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_moves WHERE item_id = :itemId AND company_id IS :companyId")
    suspend fun count(itemId: Long, companyId: Long?): Int

    @Query("SELECT * FROM stock_moves WHERE item_id = :itemId AND company_id IS :companyId ORDER BY at DESC, id DESC")
    fun observeMoves(itemId: Long, companyId: Long?): Flow<List<StockMoveEntity>>

    @Insert
    suspend fun insert(move: StockMoveEntity): Long

    /** Fires on any change of stock or of the catalog, so the masters' copy can be brought up to date. */
    @Query("SELECT (SELECT COUNT(*) FROM stock_moves) + (SELECT COUNT(*) FROM items)")
    fun observeChanges(): Flow<Int>

    @Query("SELECT * FROM attached_stock ORDER BY name COLLATE NOCASE")
    fun observeAttachedStock(): Flow<List<AttachedStockEntity>>

    @Upsert
    suspend fun upsertAttachedStock(line: AttachedStockEntity)

    @Query("DELETE FROM attached_stock WHERE attached_company_id = :companyId AND doc_id = :docId")
    suspend fun deleteAttachedStock(companyId: Long, docId: String)

    /** Reopening an order: the goods it took go back. */
    @Query("DELETE FROM stock_moves WHERE order_id = :orderId AND kind = 'ORDER'")
    suspend fun deleteOrderMoves(orderId: Long)

    /** Switching to one shared warehouse: every company's stock goes into it. */
    @Query("UPDATE stock_moves SET company_id = NULL")
    suspend fun mergeIntoShared()

    /** Switching to one warehouse per company: what was shared goes to [companyId]. */
    @Query("UPDATE stock_moves SET company_id = :companyId WHERE company_id IS NULL")
    suspend fun splitSharedTo(companyId: Long)
}
