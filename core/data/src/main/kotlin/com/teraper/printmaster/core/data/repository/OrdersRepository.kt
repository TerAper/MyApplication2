package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Planned visits. Everything here is for the company being viewed. */
interface OrdersRepository {
    fun observeOrdersOn(date: LocalDate): Flow<List<Order>>

    /** Unfinished orders from days before [date]. */
    fun observeOverdue(date: LocalDate): Flow<List<Order>>

    /** How many (not cancelled) orders each day between [from] and [to] (inclusive) has. */
    fun observeDayCounts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    fun observeClientOrders(clientId: Long): Flow<List<Order>>

    fun observeOrder(id: Long): Flow<Order?>

    /** A new order goes to the company being viewed. */
    suspend fun saveOrder(draft: OrderDraft): SaveOrderResult

    suspend fun setStatus(id: Long, status: OrderStatus)

    suspend fun deleteOrder(id: Long): DeleteOrderResult
}

sealed interface SaveOrderResult {
    data class Saved(val orderId: Long) : SaveOrderResult
    data class Invalid(val errors: Set<OrderDraftError>) : SaveOrderResult
}

enum class DeleteOrderResult { DELETED, NOT_FOUND, HAS_RECORDS }
