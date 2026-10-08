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

    @Query("SELECT * FROM repair_parts WHERE archived = 0")
    fun observeParts(): Flow<List<RepairPartEntity>>

    @Query("SELECT * FROM repair_parts WHERE id = :id AND archived = 0")
    fun observePart(id: Long): Flow<RepairPartEntity?>

    @Query("SELECT * FROM repair_parts WHERE id = :id")
    suspend fun getPart(id: Long): RepairPartEntity?

    @Query("SELECT * FROM repair_parts WHERE category = :category AND archived = 0")
    suspend fun getPartsIn(category: RepairCategory): List<RepairPartEntity>

    @Insert
    suspend fun insert(part: RepairPartEntity): Long

    @Update
    suspend fun update(part: RepairPartEntity)

    /** Repairs keep their own copy of name and prices, so deleting only unlinks them. */
    @Query("DELETE FROM repair_parts WHERE id = :id")
    suspend fun delete(id: Long): Int
}
