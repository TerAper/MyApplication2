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
    /** [finishedAt]: when it was finished, e.g. on the master's phone; null = now. */
    suspend fun finishOrder(orderId: Long, paidInCash: Boolean, finishedAt: java.time.Instant? = null): FinishOrderResult

    /** Undoes [finishOrder]: removes its charges and cash, puts its goods back, and the order is in progress again. */
    suspend fun reopenOrder(orderId: Long)

    /**
     * The goods [itemId] the open order [orderId] lacks were bought: they come into the warehouse
     * (at the line's cost) and nothing of them is "to buy" any more. False when nothing was missing.
     */
    suspend fun markBought(orderId: Long, itemId: Long): Boolean

    /**
     * The master says yes to an order another company gave him. One given to him as his own moves
     * into his company [companyId] (the client is copied there, or found by tax ID or name): his
     * prices, his money; the giving company only follows it. One he does for the company stays its.
     */
    suspend fun acceptOrder(orderId: Long, companyId: Long?): Boolean

    /**
     * The master turns down an order another company gave him (before or after accepting it);
     * it goes back to the company with [reason] and can't be worked on here any more.
     */
    suspend fun declineOrder(orderId: Long, reason: String): Boolean
}

sealed interface SaveRepairResult {
    data class Saved(val repairId: Long) : SaveRepairResult
    data class Invalid(val errors: Set<RepairDraftError>) : SaveRepairResult

    /** The order is finished or cancelled; reopen it to change the work. */
    data object Locked : SaveRepairResult
}

enum class FinishOrderResult {
    FINISHED, NO_WORK, NOT_OPEN, NOT_FOUND,

    /** Some goods are "to buy" or not in the warehouse: buy them or take them off first. */
    GOODS_MISSING,
}
