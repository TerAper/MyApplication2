package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.DeleteOrderResult
import com.teraper.printmaster.core.data.repository.OrdersRepository
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** In-memory orders; saved drafts are recorded and turned into orders for client "Client". */
class FakeOrdersRepository(initial: List<Order> = emptyList()) : OrdersRepository {
    val orders = MutableStateFlow(initial)
    val saved = mutableListOf<OrderDraft>()
    var blockedIds = emptySet<Long>()

    override fun observeOrdersOn(date: LocalDate): Flow<List<Order>> =
        orders.map { list -> list.filter { it.date == date }.sortedBy { it.scheduledAt } }

    override fun observeOverdue(date: LocalDate): Flow<List<Order>> =
        orders.map { list -> list.filter { it.date < date && it.isOpen }.sortedBy { it.scheduledAt } }

    override fun observeDayCounts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = orders.map { list ->
        list.filter { it.status != OrderStatus.CANCELLED && it.date in from..to }.groupingBy { it.date }.eachCount()
    }

    override fun observeClientOrders(clientId: Long): Flow<List<Order>> =
        orders.map { list -> list.filter { it.clientId == clientId }.sortedByDescending { it.scheduledAt } }

    override fun observeOrder(id: Long): Flow<Order?> = orders.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun saveOrder(draft: OrderDraft): SaveOrderResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveOrderResult.Invalid(errors)
        saved += draft
        val id = if (draft.isNew) (orders.value.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
        val old = orders.value.firstOrNull { it.id == id }
        val order = Order(
            id = id, companyId = 1, clientId = draft.clientId!!, clientName = old?.clientName ?: "Client",
            scheduledAt = draft.scheduledAt, description = draft.description.trim(), status = old?.status ?: OrderStatus.NEW,
            addressId = draft.addressId, phoneId = draft.phoneId, masterId = draft.masterId,
        )
        orders.value = orders.value.filterNot { it.id == id } + order
        return SaveOrderResult.Saved(id)
    }

    override suspend fun setStatus(id: Long, status: OrderStatus) {
        orders.value = orders.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun deleteOrder(id: Long): DeleteOrderResult = when {
        orders.value.none { it.id == id } -> DeleteOrderResult.NOT_FOUND
        id in blockedIds -> DeleteOrderResult.HAS_RECORDS
        else -> {
            orders.value = orders.value.filterNot { it.id == id }
            DeleteOrderResult.DELETED
        }
    }
}
