package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.DeleteOrderResult
import com.teraper.printmaster.core.data.repository.OfflineOrdersRepository
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderDraftError
import com.teraper.printmaster.core.model.OrderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class OfflineOrdersRepositoryTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneId.of("Asia/Yerevan"))
    private val repos = TestRepos(clock)
    private val orders = OfflineOrdersRepository(repos.db.orderDao(), repos.db.clientDao(), repos.companies, clock)
    private val today = LocalDate.of(2026, 10, 8)
    private var companyId = 0L
    private var clientId = 0L

    @Before
    fun setUp() = runTest {
        companyId = repos.register()
        clientId = (
            repos.clients.saveClient(
                ClientDraft(name = "Firm", phones = listOf(ContactDraft(value = "091")), addresses = listOf(ContactDraft(value = "Komitas 5"))),
            ) as SaveClientResult.Saved
            ).clientId
    }

    @After
    fun tearDown() = repos.db.close()

    private suspend fun add(date: LocalDate, hour: Int, text: String = "Refill"): Long {
        val client = repos.clients.observeClientSummary(clientId).first()!!.client
        val draft = OrderDraft(date = date, time = LocalTime.of(hour, 0), description = text).withClient(client)
        return (orders.saveOrder(draft) as SaveOrderResult.Saved).orderId
    }

    @Test
    fun ordersAreListedPerDayWithContactsInTimeOrder() = runTest {
        add(today, 14, "Second")
        add(today, 9, "First")
        add(today.plusDays(1), 10, "Tomorrow")

        val list = orders.observeOrdersOn(today).first()
        assertEquals(listOf("First", "Second"), list.map { it.description })
        assertEquals("Firm", list.first().clientName)
        assertEquals("Komitas 5", list.first().address)
        assertEquals("091", list.first().phone)
        assertEquals(LocalTime.of(9, 0), list.first().scheduledAt.toLocalTime())
        assertEquals(mapOf(today to 2, today.plusDays(1) to 1), orders.observeDayCounts(today, today.plusDays(6)).first())
    }

    @Test
    fun overdueAreOpenOrdersFromEarlierDays() = runTest {
        val missed = add(today.minusDays(2), 10, "Missed")
        val done = add(today.minusDays(1), 10, "Done")
        orders.setStatus(done, OrderStatus.DONE)
        add(today, 10, "Today")

        assertEquals(listOf(missed), orders.observeOverdue(today).first().map { it.id })
    }

    @Test
    fun ordersBelongToTheActiveCompany() = runTest {
        add(today, 10, "Main")
        val other = repos.addCompany("Other")
        repos.companies.selectCompany(other)
        assertTrue(orders.observeOrdersOn(today).first().isEmpty())
        add(today, 11, "Other's")
        assertEquals(listOf("Other's"), orders.observeOrdersOn(today).first().map { it.description })
        assertEquals(other, orders.observeOrdersOn(today).first().single().companyId)
    }

    @Test
    fun editKeepsCompanyAndStatusAndValidationWorks() = runTest {
        val id = add(today, 10)
        orders.setStatus(id, OrderStatus.IN_PROGRESS)
        val order = orders.observeOrder(id).first()!!
        orders.saveOrder(OrderDraft.from(order).copy(description = "Changed", time = LocalTime.of(15, 30)))

        val after = orders.observeOrder(id).first()!!
        assertEquals("Changed", after.description)
        assertEquals(OrderStatus.IN_PROGRESS, after.status)
        assertEquals(LocalTime.of(15, 30), after.scheduledAt.toLocalTime())
        assertEquals(
            SaveOrderResult.Invalid(setOf(OrderDraftError.CLIENT_REQUIRED)),
            orders.saveOrder(OrderDraft(clientId = 999, date = today, description = "x")),
        )
    }

    @Test
    fun deleteIsBlockedByPaymentsAndClientWithOrdersCannotBeDeleted() = runTest {
        val id = add(today, 10)
        repos.sql(
            "INSERT INTO payments (company_id, client_id, method, amount_minor, date_epoch_day, order_id, note, created_at) " +
                "VALUES ($companyId, $clientId, 'CASH', 100, 0, $id, '', 0)",
        )
        assertEquals(DeleteOrderResult.HAS_RECORDS, orders.deleteOrder(id))

        val free = add(today, 11)
        assertEquals(DeleteOrderResult.DELETED, orders.deleteOrder(free))
        assertEquals(DeleteOrderResult.NOT_FOUND, orders.deleteOrder(free))
    }
}
