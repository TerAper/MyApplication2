package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.core.model.RepairDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private val space = FakeSharedSpace()
    private val company = TestRepos()
    // The master finishes the job later in the day than the company's clock says.
    private val master = TestRepos(Clock.fixed(Instant.parse("2026-10-07T14:30:00Z"), ZoneOffset.UTC))
    private val companyEngine = company.syncEngine(space.member("owner", isOwner = true))
    private val masterEngine = master.syncEngine(space.member("u-armen"))

    @After
    fun tearDown() {
        company.db.close()
        master.db.close()
    }

    private suspend fun companySetup(): Long {
        company.companies.register(AccountMode.COMPANY, "", CompanyDraft(name = "Alfa"))
        company.companies.saveMaster(0, "Armen", "")
        company.sql("UPDATE masters SET member_uid = 'u-armen'")
        val armen = company.db.openHelper.readableDatabase.query("SELECT id FROM masters").use { it.moveToFirst(); it.getLong(0) }
        company.priceList.saveItem(PriceItemDraft(category = RepairCategory.CARTRIDGE, name = "Refill 85A", priceDigits = "3000", costDigits = "900"))
        val client = (company.clients.saveClient(ClientDraft(name = "Gamma", phones = listOf(ContactDraft(value = "091 111111")), addresses = listOf(ContactDraft(value = "Komitas 5")))) as SaveClientResult.Saved).clientId
        val model = PrinterModelDraft(brand = "HP", name = "M125", cartridges = listOf(CartridgeDraft("CF283A")))
        company.printers.savePrinter(ClientPrinterDraft(clientId = client, model = model, selectedCartridges = setOf(model.cartridges.single().key))) as SavePrinterResult.Saved
        val summary = company.clients.observeClientSummary(client).first()!!
        company.orders.saveOrder(
            OrderDraft(
                clientId = client, masterId = armen, date = LocalDate.of(2026, 10, 7), description = "Refill two cartridges",
                addressId = summary.client.addresses.single().id, phoneId = summary.client.phones.single().id,
            ),
        ) as SaveOrderResult.Saved
        // Not assigned to anyone who joined: stays on the company's phone.
        company.orders.saveOrder(OrderDraft(clientId = client, date = LocalDate.of(2026, 10, 7), description = "Private visit"))
        return client
    }

    private suspend fun masterSetup() {
        (master.companies.register(AccountMode.JOINED, "Armen", CompanyDraft(name = "Alfa")) as SaveCompanyResult.Saved)
    }

    @Test
    fun masterGetsHisOrderFinishesItAndTheCompanyBillsIt() = runTest {
        val clientId = companySetup()
        masterSetup()
        assertEquals(3, companyEngine.sync().sent) // order, its client, the price item

        // Master: only his order, the client with contacts and printer, the prices (no costs).
        val received = masterEngine.sync()
        assertEquals(1, received.newOrders)
        val masterClient = master.clients.observeClientSummaries().first().single()
        assertEquals("Gamma", masterClient.client.name)
        assertEquals(listOf("091 111111"), masterClient.client.phones.map { it.number })
        assertEquals(Money.ZERO, masterClient.charged)
        val order = master.orders.observeClientOrders(masterClient.client.id).first().single()
        assertEquals("Refill two cartridges", order.description)
        assertEquals("Komitas 5", order.address)
        val printer = master.printers.observeClientPrinters(masterClient.client.id).first().single()
        val refill = master.priceList.observeItems().first().single()
        assertEquals(Money.ZERO, refill.cost)

        // Master does the job: refill ×2 on the cartridge, paid in cash.
        master.repairs.saveRepair(RepairDraft(orderId = order.id).withDevice(printer.id, printer.cartridges.single().id).plus(refill).plus(refill))
        master.repairs.finishOrder(order.id, paidInCash = true)
        assertTrue(masterEngine.sync().sent >= 1)

        // Company: the order is done at the master's time, the client charged and paid, costs from its own price list.
        val report = companyEngine.sync()
        assertEquals(1, report.finishedOrders)
        val companyOrder = company.orders.observeClientOrders(clientId).first().first { it.description == "Refill two cartridges" }
        assertEquals(OrderStatus.DONE, companyOrder.status)
        assertEquals(14, companyOrder.doneAt?.hour)
        val work = company.repairs.observeOrderWork(companyOrder.id).first()
        assertEquals(Money.ofDram(6_000), work.total)
        assertEquals(Money.ofDram(4_200), work.profit)
        assertEquals("CF283A · HP M125", work.repairs.single().device?.name)
        val ledger = company.payments.observeLedger(clientId).first()
        assertEquals(Money.ofDram(6_000), ledger.filterIsInstance<LedgerEntry.Payment>().single { it.method == PaymentMethod.CASH }.amount)

        // Nothing echoes back: the company has nothing new to send for what it received.
        assertEquals(0, companyEngine.sync().sent)
        assertEquals(1, space.orders().size)
    }

    @Test
    fun masterAddsAClientAndOrderTheCompanyReviews() = runTest {
        companySetup()
        masterSetup()
        companyEngine.sync()
        masterEngine.sync()

        val delta = (master.clients.saveClient(ClientDraft(name = "Delta", phones = listOf(ContactDraft(value = "093 222222")))) as SaveClientResult.Saved).clientId
        master.orders.saveOrder(OrderDraft(clientId = delta, date = LocalDate.of(2026, 10, 8), description = "New client, check printer"))
        masterEngine.sync()

        val report = companyEngine.sync()
        assertEquals(1, report.newClients)
        assertEquals(1, report.newOrders)
        val newClient = company.clients.observeClientSummaries().first().single { it.client.name == "Delta" }
        val needsReview = company.db.openHelper.readableDatabase.query("SELECT needs_review FROM clients WHERE id = ${newClient.client.id}").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, needsReview)
        val order = company.orders.observeClientOrders(newClient.client.id).first().single()
        assertNotNull(order.masterName)
        assertEquals("Armen", order.masterName)
    }
}
