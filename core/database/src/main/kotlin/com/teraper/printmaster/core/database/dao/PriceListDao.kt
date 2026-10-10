package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.model.RepairCategory
import kotlinx.coroutines.flow.Flow

/** The price list (table repair_parts). Sorting by name is done in Kotlin, for Armenian letters. */
@Dao
interface PriceListDao {

    /** The user's own price list. */
    @Query("SELECT * FROM repair_parts WHERE archived = 0 AND attached_company_id IS NULL")
    fun observeParts(): Flow<List<RepairPartEntity>>

    /** The price list for work on an order of [companyId]: an attached company's own list, else the user's. */
    @Query(
        """
        SELECT * FROM repair_parts WHERE archived = 0 AND (
            CASE WHEN (SELECT kind FROM companies WHERE id = :companyId) = 'ATTACHED'
            THEN attached_company_id = :companyId ELSE attached_company_id IS NULL END)
        """,
    )
    fun observePartsFor(companyId: Long): Flow<List<RepairPartEntity>>

    /** An attached company's price list, used for its orders. */
    @Query("SELECT * FROM repair_parts WHERE archived = 0 AND attached_company_id = :companyId")
    fun observeAttachedParts(companyId: Long): Flow<List<RepairPartEntity>>

    @Query("SELECT * FROM repair_parts WHERE id = :id AND archived = 0")
    fun observePart(id: Long): Flow<RepairPartEntity?>

    @Query("SELECT * FROM repair_parts WHERE id = :id")
    suspend fun getPart(id: Long): RepairPartEntity?

    @Query("SELECT * FROM repair_parts WHERE category = :category AND archived = 0 AND attached_company_id IS NULL")
    suspend fun getPartsIn(category: RepairCategory): List<RepairPartEntity>

    @Insert
    suspend fun insert(part: RepairPartEntity): Long

    @Update
    suspend fun update(part: RepairPartEntity)

    /** Repairs keep their own copy of name and prices, so deleting only unlinks them. */
    @Query("DELETE FROM repair_parts WHERE id = :id")
    suspend fun delete(id: Long): Int
}
