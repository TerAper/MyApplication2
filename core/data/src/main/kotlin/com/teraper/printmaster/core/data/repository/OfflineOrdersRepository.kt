package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.OrderRow
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.model.Order
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

internal class OfflineOrdersRepository @Inject constructor(
    private val orderDao: OrderDao,
    private val clientDao: ClientDao,
    private val companies: CompaniesRepository,
    private val clock: Clock,
) : OrdersRepository {

    private val zone get() = clock.zone

    private fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    private fun LocalDateTime.millis(): Long = atZone(zone).toInstant().toEpochMilli()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forActiveCompany(empty: T, block: (companyId: Long) -> Flow<T>): Flow<T> =
        companies.observeActiveCompany().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(empty) else block(id)
        }

    override fun observeOrdersOn(date: LocalDate): Flow<List<Order>> = forActiveCompany(emptyList()) { companyId ->
        orderDao.observeOrdersBetween(companyId, date.startMillis(), date.plusDays(1).startMillis()).map { it.toOrders() }
    }

    override fun observeOverdue(date: LocalDate): Flow<List<Order>> = forActiveCompany(emptyList()) { companyId ->
        orderDao.observeOpenOrdersBefore(companyId, date.startMillis()).map { it.toOrders() }
    }

    override fun observeDayCounts(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = forActiveCompany(emptyMap()) { companyId ->
        orderDao.observeOrderTimes(companyId, from.startMillis(), to.plusDays(1).startMillis()).map { times ->
            times.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount()
        }
    }

    override fun observeClientOrders(clientId: Long): Flow<List<Order>> = forActiveCompany(emptyList()) { companyId ->
        orderDao.observeClientOrders(companyId, clientId).map { it.toOrders() }
    }

    override fun observeOrder(id: Long): Flow<Order?> = orderDao.observeOrder(id).map { it?.toOrder() }

    override suspend fun saveOrder(draft: OrderDraft): SaveOrderResult {
        val errors = draft.validate().toMutableSet()
        val clientId = draft.clientId
        val client = clientId?.let { clientDao.getClient(it) }
        if (clientId != null && client == null) errors += OrderDraftError.CLIENT_REQUIRED
        if (errors.isNotEmpty() || clientId == null) return SaveOrderResult.Invalid(errors)

        val description = draft.description.trim()
        return if (draft.isNew) {
            val companyId = companies.observeActiveCompany().first()?.id ?: return SaveOrderResult.Invalid(emptySet())
            val id = orderDao.insertOrder(
                OrderEntity(
                    companyId = companyId,
                    masterId = draft.masterId,
                    clientId = clientId,
                    scheduledAt = draft.scheduledAt.millis(),
                    addressId = draft.addressId,
                    phoneId = draft.phoneId,
                    description = description,
                    createdAt = clock.millis(),
                ),
            )
            SaveOrderResult.Saved(id)
        } else {
            // Editing keeps the order's company and status.
            val existing = orderDao.getOrder(draft.id) ?: return SaveOrderResult.Invalid(emptySet())
            orderDao.updateOrder(
                existing.copy(
                    masterId = draft.masterId,
                    clientId = clientId,
                    scheduledAt = draft.scheduledAt.millis(),
                    addressId = draft.addressId,
                    phoneId = draft.phoneId,
                    description = description,
                ),
            )
            SaveOrderResult.Saved(existing.id)
        }
    }

    override suspend fun setStatus(id: Long, status: OrderStatus) {
        orderDao.setStatus(id, status)
    }

    override suspend fun deleteOrder(id: Long): DeleteOrderResult = when {
        orderDao.getOrder(id) == null -> DeleteOrderResult.NOT_FOUND
        orderDao.countRecordsBlockingDelete(id) > 0 -> DeleteOrderResult.HAS_RECORDS
        else -> {
            orderDao.deleteOrder(id)
            DeleteOrderResult.DELETED
        }
    }

    private fun List<OrderRow>.toOrders() = map { it.toOrder() }

    private fun OrderRow.toOrder() = Order(
        id = order.id,
        companyId = order.companyId,
        clientId = order.clientId,
        clientName = clientName,
        scheduledAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(order.scheduledAt), zone),
        description = order.description,
        status = order.status,
        addressId = order.addressId,
        address = address,
        addressLink = addressLink,
        phoneId = order.phoneId,
        phone = phone,
        masterId = order.masterId,
        masterName = masterName,
    )
}
