package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.FinishOrderResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyKind
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Two owners on two phones: Apo owns Xerox (and Yellow), Armen owns Delta and attached to Xerox
 * with its code. Apo gives Armen a Xerox order; Armen does it for Xerox, takes it as his own, or turns it down.
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private val cloud = FakeSharedSpace()
    private val apo = TestRepos()
    // Armen finishes later in the day than Apo's clock says.
    private val armen = TestRepos(Clock.fixed(Instant.parse("2026-10-07T14:30:00Z"), ZoneOffset.UTC))
    private val apoEngine = apo.syncEngine(cloud.phone("u-apo"))
    private val armenEngine = armen.syncEngine(cloud.phone("u-armen"))
    private val day = LocalDate.of(2026, 10, 7)

    private var xerox = 0L
    private var yellow = 0L
    private var gamma = 0L
    private var delta = 0L
    private var armenXerox = 0L

    @After
    fun tearDown() {
        apo.db.close()
        armen.db.close()
    }

    /** Apo: Xerox invited masters (space w-x), Armen entered its code; one order for Armen, one Apo does himself. */
    private suspend fun setUp() {
        xerox = (apo.companies.register(AccountMode.OWNER, "Apo", CompanyDraft(name = "Xerox")) as SaveCompanyResult.Saved).companyId
        yellow = apo.addCompany("Yellow")
        apo.sql("UPDATE companies SET space_id = 'w-x', join_code = 'XCODE123' WHERE id = $xerox")
        cloud.attach("w-x", "u-armen", "Armen")
        apo.priceList.saveItem(PriceItemDraft(category = RepairCategory.CARTRIDGE, name = "Refill 85A", priceDigits = "3000", costDigits = "900"))
        gamma = (apo.clients.saveClient(ClientDraft(name = "Gamma", taxId = "02920288", phones = listOf(ContactDraft(value = "091 111111")), addresses = listOf(ContactDraft(value = "Komitas 5")))) as SaveClientResult.Saved).clientId
        val model = PrinterModelDraft(brand = "HP", name = "M125", cartridges = listOf(CartridgeDraft("CF283A")))
        apo.printers.savePrinter(ClientPrinterDraft(clientId = gamma, model = model, selectedCartridges = setOf(model.cartridges.single().key))) as SavePrinterResult.Saved

        // Armen: his own Delta, and Xerox attached with its code.
        delta = (armen.companies.register(AccountMode.OWNER, "Armen", CompanyDraft(name = "Delta")) as SaveCompanyResult.Saved).companyId
        armenXerox = armen.db.companyDao().insertCompany(CompanyEntity(name = "Xerox", taxId = null, createdAt = 0, kind = CompanyKind.ATTACHED, spaceId = "w-x", ownerName = "Apo"))

        // First sync links Armen as Xerox's attached master.
        apoEngine.sync()
        val armenMaster = apo.companies.observeMasters().first().single()
        assertEquals(setOf(xerox), armenMaster.companyIds)
        val summary = apo.clients.observeClientSummary(gamma).first()!!
        apo.orders.saveOrder(
            OrderDraft(
                clientId = gamma, masterId = armenMaster.id, date = day, description = "Refill two cartridges",
                addressId = summary.client.addresses.single().id, phoneId = summary.client.phones.single().id,
            ),
        ) as SaveOrderResult.Saved
        apo.orders.saveOrder(OrderDraft(clientId = gamma, date = day, description = "Apo's own visit"))
        apoEngine.sync()
    }

    private suspend fun armenOrderId(): Long = armen.orders.observeOrdersOn(day).first().single { it.fromAttachedCompany }.id

    private suspend fun apoOrder() = apo.orders.observeClientOrders(gamma).first().first { it.description == "Refill two cartridges" }

    /** Armen's work: refill ×2 from Xerox's price list on Gamma's cartridge. */
    private suspend fun armenDoesTheWork(orderId: Long) {
        val client = armen.orders.observeOrder(orderId).first()!!.clientId
        val printer = armen.printers.observeClientPrinters(client).first().single()
        val refill = armen.priceList.observeItemsFor(armenXerox).first().single()
        armen.repairs.saveRepair(RepairDraft(orderId = orderId).withDevice(printer.id, printer.cartridges.single().id).plus(refill).plus(refill))
    }

    @Test
    fun masterSeesXeroxOrderApartFromHisOwnAndDoesItForXerox() = runTest {
        setUp()
        val received = armenEngine.sync()
        assertEquals(1, received.newOrders)
        // Only Armen's order went out; Apo's own visit stayed on Apo's phone.
        assertEquals(1, cloud.orders("w-x").size)

        // Xerox's order is in Armen's day, marked; Xerox's client and prices aren't mixed with his own.
        val order = armen.orders.observeOrdersOn(day).first().single()
        assertTrue(order.fromAttachedCompany)
        assertEquals("Xerox", order.companyName)
        assertEquals("Komitas 5", order.address)
        assertTrue(armen.clients.observeClientSummaries().first().isEmpty())
        assertTrue(armen.priceList.observeItems().first().isEmpty())
        val refill = armen.priceList.observeItemsFor(armenXerox).first().single()
        assertEquals(Money.ZERO, refill.cost)
        assertTrue(armen.clients.observeClientSummary(order.clientId).first()!!.client.isAttached)

        armenDoesTheWork(order.id)
        assertEquals(FinishOrderResult.FINISHED, armen.repairs.finishOrder(order.id, paidInCash = true))
        // No money on Armen's phone for Xerox's client.
        assertEquals(0, armen.count("charges"))
        assertEquals(0, armen.count("payments"))
        armenEngine.sync()

        // Apo: done at Armen's time, Gamma charged and paid in cash, costs from Apo's own list.
        assertEquals(1, apoEngine.sync().finishedOrders)
        val done = apoOrder()
        assertEquals(OrderStatus.DONE, done.status)
        assertEquals(14, done.doneAt?.hour)
        assertFalse(done.takenByMaster)
        val work = apo.repairs.observeOrderWork(done.id).first()
        assertEquals(Money.ofDram(6_000), work.total)
        assertEquals(Money.ofDram(4_200), work.profit)
        assertEquals("CF283A · HP M125", work.repairs.single().device?.name)
        val ledger = apo.payments.observeLedger(gamma).first()
        assertEquals(Money.ofDram(6_000), ledger.filterIsInstance<LedgerEntry.Payment>().single { it.method == PaymentMethod.CASH }.amount)
        // Nothing echoes back.
        assertEquals(0, apoEngine.sync().sent)
    }

    @Test
    fun masterTakesTheOrderAsHisOwnAndXeroxKeepsOnlyHistory() = runTest {
        setUp()
        armenEngine.sync()
        val orderId = armenOrderId()
        armenDoesTheWork(orderId)

        assertEquals(FinishOrderResult.FINISHED, armen.repairs.finishAsMine(orderId, delta, paidInCash = false))
        // Armen: the order is Delta's now, Gamma is his own client and owes Delta.
        val mine = armen.orders.observeOrder(orderId).first()!!
        assertEquals(delta, mine.companyId)
        assertTrue(mine.takenByMaster)
        val ownGamma = armen.clients.observeClientSummaries().first().single()
        assertEquals("Gamma", ownGamma.client.name)
        assertEquals("02920288", ownGamma.client.taxId)
        assertEquals(Money.ofDram(6_000), ownGamma.charged)
        assertEquals("CF283A · HP M125", armen.repairs.observeOrderWork(orderId).first().repairs.single().device?.name)
        armenEngine.sync()

        // Apo: done by Armen as his own; no work, no money for Gamma.
        assertEquals(1, apoEngine.sync().finishedOrders)
        val history = apoOrder()
        assertEquals(OrderStatus.DONE, history.status)
        assertTrue(history.takenByMaster)
        assertTrue(apo.repairs.observeOrderWork(history.id).first().repairs.isEmpty())
        assertTrue(apo.payments.observeLedger(gamma).first().isEmpty())
    }

    @Test
    fun masterTurnsTheOrderDownAndItGoesBackToApo() = runTest {
        setUp()
        armenEngine.sync()
        assertTrue(armen.repairs.declineOrder(armenOrderId(), "Too far today"))
        armenEngine.sync()

        assertEquals(1, apoEngine.sync().declinedOrders)
        val back = apoOrder()
        assertEquals(OrderStatus.NEW, back.status)
        assertNull(back.masterId)
        assertEquals("Armen", back.declinedBy)
        assertEquals("Too far today", back.declinedReason)
    }

    @Test
    fun ordersOfACompanyTheMasterDidNotAttachToStayHome() = runTest {
        setUp()
        val armenMaster = apo.companies.observeMasters().first().single()
        assertFalse(armenMaster.canWorkFor(yellow))
        apo.sql("UPDATE companies SET space_id = 'w-y', join_code = 'YCODE123' WHERE id = $yellow")
        apo.orders.saveOrder(OrderDraft(clientId = gamma, date = day, description = "Yellow job"))
        val yellowOrder = apo.db.openHelper.readableDatabase.query("SELECT id FROM orders WHERE description = 'Yellow job'").use { it.moveToFirst(); it.getLong(0) }
        // Even if it gets assigned to him by mistake, Yellow's space never sends it.
        apo.sql("UPDATE orders SET master_id = ${armenMaster.id}, company_id = $yellow WHERE id = $yellowOrder")
        apoEngine.sync()
        assertTrue(cloud.orders("w-y").isEmpty())
    }

    @Test
    fun twoPrintersOfOneModelStayTwoAndWorkGoesToTheRightOne() = runTest {
        setUp()
        val model = PrinterModelDraft(brand = "HP", name = "M125", cartridges = listOf(CartridgeDraft("CF283A")))
        apo.printers.savePrinter(ClientPrinterDraft(clientId = gamma, model = model, selectedCartridges = setOf(model.cartridges.single().key), location = "Accounting"))
        apoEngine.sync()
        armenEngine.sync()

        val orderId = armenOrderId()
        val client = armen.orders.observeOrder(orderId).first()!!.clientId
        val printers = armen.printers.observeClientPrinters(client).first()
        assertEquals(2, printers.size)
        val accounting = printers.single { it.location == "Accounting" }
        val refill = armen.priceList.observeItemsFor(armenXerox).first().single()
        armen.repairs.saveRepair(RepairDraft(orderId = orderId).withDevice(accounting.id, accounting.cartridges.single().id).plus(refill))
        armen.repairs.finishOrder(orderId, paidInCash = false)
        armenEngine.sync()
        // A second pull must not merge or duplicate the printers.
        armenEngine.sync()
        assertEquals(2, armen.printers.observeClientPrinters(client).first().size)

        apoEngine.sync()
        val device = apo.repairs.observeOrderWork(apoOrder().id).first().repairs.single().device!!
        assertEquals("Accounting", device.printer.location)
        assertEquals("CF283A · HP M125 (Accounting)", device.name)
    }
}
