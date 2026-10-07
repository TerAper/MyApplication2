package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

/** Charges (what clients owe) and payments (what they paid). */
@Dao
interface LedgerDao {

    @Query("SELECT * FROM charges WHERE client_id = :clientId")
    fun observeCharges(clientId: Long): Flow<List<ChargeEntity>>

    @Query("SELECT * FROM payments WHERE client_id = :clientId")
    fun observePayments(clientId: Long): Flow<List<PaymentEntity>>

    /** Money received between two days (inclusive), per payment method. */
    @Query(
        """
        SELECT method, SUM(amount_minor) AS total FROM payments
        WHERE client_id IS NOT NULL AND date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY method
        """,
    )
    fun observeIncomeByMethod(fromEpochDay: Long, toEpochDay: Long): Flow<List<MethodTotal>>

    @Insert
    suspend fun insertCharge(charge: ChargeEntity): Long

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    /** Only hand-typed debts; returns rows deleted (0 = not allowed or not found). */
    @Query("DELETE FROM charges WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManualCharge(id: Long): Int

    /** Only cash payments; bank payments go away by undoing their import. */
    @Query("DELETE FROM payments WHERE id = :id AND method = 'CASH'")
    suspend fun deleteCashPayment(id: Long): Int
}

data class MethodTotal(
    val method: PaymentMethod,
    @ColumnInfo(name = "total") val totalMinor: Long,
)
