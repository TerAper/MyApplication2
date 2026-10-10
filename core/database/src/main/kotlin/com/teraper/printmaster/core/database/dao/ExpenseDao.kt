package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.teraper.printmaster.core.database.entity.ExpenseEntity
import com.teraper.printmaster.core.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expenses WHERE company_id = :companyId AND date_epoch_day BETWEEN :from AND :to
        ORDER BY date_epoch_day DESC, created_at DESC
        """,
    )
    fun observeExpenses(companyId: Long, from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT category, SUM(amount_minor) AS total FROM expenses
        WHERE company_id = :companyId AND date_epoch_day BETWEEN :from AND :to GROUP BY category
        """,
    )
    fun observeTotals(companyId: Long, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeExpense(id: Long): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: Long): ExpenseEntity?

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long): Int
}

data class CategoryTotal(
    val category: ExpenseCategory,
    @ColumnInfo(name = "total") val totalMinor: Long,
)
