package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * Totals for reports, always for one company and between two days (inclusive).
 * Like the client balances, imported rows not yet matched to a client are left out.
 */
@Dao
interface ReportDao {

    @Query(
        """
        SELECT source, SUM(amount_minor) AS total FROM charges
        WHERE client_id IS NOT NULL AND company_id = :companyId
          AND date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        GROUP BY source
        """,
    )
    fun observeBilledBySource(companyId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<SourceTotal>>

    /** Repair work billed in the period, priced as it was sold (not today's price list). */
    @Query(
        """
        SELECT COUNT(DISTINCT r.order_id) AS orders,
               COALESCE(SUM(i.price_minor * i.quantity), 0) AS revenue,
               COALESCE(SUM(i.cost_minor * i.quantity), 0) AS cost
        FROM charges c
        JOIN repairs r ON r.id = c.repair_id
        JOIN repair_items i ON i.repair_id = r.id
        WHERE c.source = 'REPAIR' AND c.company_id = :companyId
          AND c.date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeWork(companyId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<WorkRow>

    /** Every payment in the period; grouped into months in Kotlin. */
    @Query(
        """
        SELECT method, amount_minor, date_epoch_day FROM payments
        WHERE client_id IS NOT NULL AND company_id = :companyId
          AND date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observePayments(companyId: Long, fromEpochDay: Long, toEpochDay: Long): Flow<List<PaymentRow>>
}

data class SourceTotal(
    val source: ChargeSource,
    @ColumnInfo(name = "total") val totalMinor: Long,
)

data class WorkRow(
    val orders: Int,
    @ColumnInfo(name = "revenue") val revenueMinor: Long,
    @ColumnInfo(name = "cost") val costMinor: Long,
)

data class PaymentRow(
    val method: PaymentMethod,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
)
