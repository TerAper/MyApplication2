package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.core.model.Repair
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.RepairDraftError
import kotlinx.coroutines.flow.Flow

/** Work done on an order, and finishing it: charging the client, with or without cash. */
interface RepairsRepository {
    fun observeOrderWork(orderId: Long): Flow<OrderWork>

    fun observeRepair(id: Long): Flow<Repair?>

    /** Only while the order is open. The first work on a new order marks it started. */
    suspend fun saveRepair(draft: RepairDraft): SaveRepairResult

    /** False when the order is already finished (or the repair is gone). */
    suspend fun deleteRepair(id: Long): Boolean

    /**
     * Marks the order done and charges the client for each repair, under the order's company.
     * With [paidInCash] the whole amount is also recorded as a cash payment.
     */
    suspend fun finishOrder(orderId: Long, paidInCash: Boolean): FinishOrderResult

    /** Undoes [finishOrder]: removes its charges and cash, and the order is in progress again. */
    suspend fun reopenOrder(orderId: Long)
}

sealed interface SaveRepairResult {
    data class Saved(val repairId: Long) : SaveRepairResult
    data class Invalid(val errors: Set<RepairDraftError>) : SaveRepairResult

    /** The order is finished or cancelled; reopen it to change the work. */
    data object Locked : SaveRepairResult
}

enum class FinishOrderResult { FINISHED, NO_WORK, NOT_OPEN, NOT_FOUND }
