package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.FinishOrderResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.model.TypeTemplate
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.ClientPartDraft
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientDraft.ContactDraft
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.RepairDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.teraper.printmaster.core.model.MastersSee
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockDraft
import com.teraper.printmaster.core.data.repository.SaveItemResult
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
    private suspend fun setUp(given: Boolean = false) {
        xerox = (apo.companies.register(AccountMode.OWNER, "Apo", CompanyDraft(name = "Xerox")) as SaveCompanyResult.Saved).companyId
        yellow = apo.addCompany("Yellow")
        apo.sql("UPDATE companies SET space_id = 'w-x', join_code = 'XCODE123' WHERE id = $xerox")
        cloud.attach("w-x", "u-armen", "Armen")
        apo.priceList.saveItem(PriceItemDraft(typeId = apo.printerTypes().second, name = "Refill 85A", priceDigits = "3000", costDigits = "900"))
        gamma = (apo.clients.saveClient(ClientDraft(name = "Gamma", taxId = "02920288", phones = listOf(ContactDraft(value = "091 111111")), addresses = listOf(ContactDraft(value = "Komitas 5")))) as SaveClientResult.Saved).clientId
        apo.addPrinter(gamma)

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
                handedOver = given,
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
        val printer = armen.clientItems.observeClientItems(client).first().single()
        val refill = armen.priceList.observeItemsFor(armenXerox).first().single()
        armen.repairs.saveRepair(RepairDraft(orderId = orderId).withDevice(printer.id, printer.parts.single().id).plus(refill).plus(refill))
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

        assertTrue(order.awaitsAnswer)
        assertTrue(armen.repairs.acceptOrder(order.id, null))
        armenEngine.sync()
        // Apo sees the yes.
        assertEquals(1, apoEngine.sync().acceptedOrders)
        assertNotNull(apoOrder().acceptedAt)

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
    fun aGivenOrderBecomesTheMastersOwnAndXeroxKeepsOnlyHistory() = runTest {
        setUp(given = true)
        armenEngine.sync()
        val orderId = armenOrderId()
        assertTrue(armen.orders.observeOrder(orderId).first()!!.handedOver)

        // Accepting puts it in Delta: Gamma becomes Armen's own client, priced from his own list.
        assertTrue(armen.repairs.acceptOrder(orderId, delta))
        val mine = armen.orders.observeOrder(orderId).first()!!
        assertEquals(delta, mine.companyId)
        assertEquals("Xerox", mine.takenFrom)
        val ownGamma = armen.clients.observeClientSummaries().first().single()
        assertEquals("Gamma", ownGamma.client.name)
        assertEquals("02920288", ownGamma.client.taxId)
        armen.priceList.saveItem(PriceItemDraft(name = "Armen refill", priceDigits = "2500", costDigits = "700"))
        val myRefill = armen.priceList.observeItemsFor(delta).first().single()
        val printer = armen.clientItems.observeClientItems(ownGamma.client.id).first().single()
        armen.repairs.saveRepair(RepairDraft(orderId = orderId).withDevice(printer.id, printer.parts.single().id).plus(myRefill))
        assertEquals(FinishOrderResult.FINISHED, armen.repairs.finishOrder(orderId, paidInCash = false))
        assertEquals(Money.ofDram(2_500), armen.clients.observeClientSummaries().first().single().charged)
        armenEngine.sync()

        // Apo: accepted, then done as Armen's own; no work, no money for Gamma.
        val report = apoEngine.sync()
        assertEquals(1, report.finishedOrders)
        val history = apoOrder()
        assertEquals(OrderStatus.DONE, history.status)
        assertTrue(history.takenByMaster)
        assertTrue(apo.repairs.observeOrderWork(history.id).first().repairs.isEmpty())
        assertTrue(apo.payments.observeLedger(gamma).first().isEmpty())
    }

    @Test
    fun anAcceptedOrderGivenBackReturnsToApo() = runTest {
        setUp(given = true)
        armenEngine.sync()
        val orderId = armenOrderId()
        armen.repairs.acceptOrder(orderId, delta)
        armenEngine.sync()
        apoEngine.sync()
        assertNotNull(apoOrder().acceptedAt)

        assertTrue(armen.repairs.declineOrder(orderId, "Car broke down"))
        assertTrue(armen.orders.observeOrder(orderId).first()!!.declinedHere)
        armenEngine.sync()
        assertEquals(1, apoEngine.sync().declinedOrders)
        val back = apoOrder()
        assertEquals(OrderStatus.NEW, back.status)
        assertNull(back.masterId)
        assertNull(back.acceptedAt)
        assertEquals("Car broke down", back.declinedReason)
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
    fun twoItemsOfOneModelStayTwoAndWorkGoesToTheRightOne() = runTest {
        setUp()
        apo.addPrinter(gamma, place = "Accounting", serial = "CNB8K12345")
        apoEngine.sync()
        armenEngine.sync()

        val orderId = armenOrderId()
        val client = armen.orders.observeOrder(orderId).first()!!.clientId
        val printers = armen.clientItems.observeClientItems(client).first()
        assertEquals(2, printers.size)
        val accounting = printers.single { it.place == "Accounting" }
        // The serial goes to the master's phone too, so he can find the printer by scanning it.
        assertEquals("CNB8K12345", accounting.serialNumber)
        assertEquals(listOf(accounting.id), armen.clientItems.findBySerial("cnb8k12345").map { it.item.id })
        val refill = armen.priceList.observeItemsFor(armenXerox).first().single()
        armen.repairs.saveRepair(RepairDraft(orderId = orderId).withDevice(accounting.id, accounting.parts.single().id).plus(refill))
        armen.repairs.finishOrder(orderId, paidInCash = false)
        armenEngine.sync()
        // A second pull must not merge or duplicate the printers.
        armenEngine.sync()
        assertEquals(2, armen.clientItems.observeClientItems(client).first().size)

        apoEngine.sync()
        val device = apo.repairs.observeOrderWork(apoOrder().id).first().repairs.single().device!!
        assertEquals("Accounting", device.item.place)
        assertEquals("CF283A · HP M125 (Accounting)", device.name)
        // On Apo's phone the serial finds the printer and the order done on it.
        val found = apo.clientItems.findBySerial("CNB8K12345").single()
        assertEquals(listOf("Refill two cartridges"), apo.orders.observeClientItemOrders(found.item.id).first().map { it.description })
    }

    @Test
    fun aChildWithItsShoeSizesReachesTheMaster() = runTest {
        setUp()
        apo.catalog.applyTemplate(
            listOf(
                TypeTemplate("Child", fields = listOf(FieldDraft(name = "Birth", kind = FieldKind.DATE)), hasSerial = false, perClient = true),
                TypeTemplate("Shoe", parentName = "Child", fields = listOf(FieldDraft(name = "Size", kind = FieldKind.NUMBER)), hasSerial = false),
            ),
        )
        val types = apo.catalog.observeTypes().first()
        val child = types.first { it.name == "Child" }
        val shoe = types.first { it.name == "Shoe" }
        apo.clientItems.saveClientItem(
            ClientItemDraft(
                clientId = gamma,
                item = ItemDraft(typeId = child.id, name = "David", values = mapOf(child.fields.single().id to "2019-03-12")),
                parts = listOf(ClientPartDraft(typeId = shoe.id, values = mapOf(shoe.fields.single().id to "30"))),
            ),
        )
        apoEngine.sync()
        armenEngine.sync()

        val client = armen.orders.observeOrder(armenOrderId()).first()!!.clientId
        val david = armen.clientItems.observeClientItems(client).first().single { it.type.perClient }
        assertEquals("David", david.title)
        assertEquals(null, david.item)
        assertEquals(listOf("2019-03-12"), david.values.values.toList())
        assertEquals(listOf("Size 30"), david.parts.map { it.title })
        assertFalse(david.type.hasSerial)
        // The child is not put in the master's catalog.
        assertTrue(armen.catalog.observeCatalog().first().none { it.item.typeId == david.type.id })
    }

    @Test
    fun mastersSeeStockCountsAndPricesOnlyWhenAllowed() = runTest {
        setUp()
        val cartridge = apo.printerTypes().second
        val toner = (apo.catalog.saveItem(ItemDraft(typeId = cartridge, name = "CE285A").withPrice("9000").withCost("6000")) as SaveItemResult.Saved).itemId
        apo.warehouse.save(StockDraft(toner).withQuantity("5"))
        apoEngine.sync()
        armenEngine.sync()

        val line = armen.warehouse.observeCompanyStock().first().single()
        assertEquals(armenXerox, line.companyId)
        assertEquals("CE285A", line.name)
        assertEquals(5, line.count)
        assertNull(line.price)
        // Costs never leave Apo's phone; nothing changed = nothing sent again.
        assertTrue(cloud.stock("w-x").none { it.toString().contains("6000") })
        assertEquals(0, apoEngine.sync().sent)

        apo.warehouse.setMastersSee(MastersSee.COUNTS_AND_PRICES)
        apo.warehouse.save(StockDraft(toner, kind = StockKind.ADJUST).withQuantity("3"))
        apoEngine.sync()
        armenEngine.sync()
        val priced = armen.warehouse.observeCompanyStock().first().single()
        assertEquals(3, priced.count)
        assertEquals(Money.ofDram(9_000), priced.price)

        // Not sold and none left: gone from Armen's list.
        apo.warehouse.save(StockDraft(toner, kind = StockKind.ADJUST).withQuantity("0"))
        apo.catalog.saveItem(ItemDraft.from(apo.catalog.observeItem(toner).first()!!).withPrice(""))
        apoEngine.sync()
        armenEngine.sync()
        assertTrue(armen.warehouse.observeCompanyStock().first().isEmpty())
    }

    @Test
    fun askingToSeeTheMasterTravelsAndHisAnswerComesBack() = runTest {
        setUp()
        // Apo asks Armen to share where he is on the order.
        val order = apoOrder()
        apo.orders.saveOrder(com.teraper.printmaster.core.model.OrderDraft.from(order).copy(askLocation = true))
        apoEngine.sync()
        armenEngine.sync()
        val id = armenOrderId()
        assertTrue(armen.orders.observeOrder(id).first()!!.askLocation)

        // Armen accepts and shares: Apo sees it live.
        assertTrue(armen.repairs.acceptOrder(id, null, shareLocation = true))
        armenEngine.sync()
        apoEngine.sync()
        assertTrue(apoOrder().locationLive)

        // Apo stops asking: Armen's phone stops sharing.
        apo.orders.saveOrder(com.teraper.printmaster.core.model.OrderDraft.from(apoOrder()).copy(askLocation = false))
        apoEngine.sync()
        armenEngine.sync()
        val after = armen.orders.observeOrder(id).first()!!
        assertTrue(!after.askLocation && !after.sharingLocation)
    }

    @Test
    fun aGivenOrderIsNeverTracked() = runTest {
        setUp(given = true)
        apo.orders.saveOrder(com.teraper.printmaster.core.model.OrderDraft.from(apoOrder()).copy(askLocation = true))
        assertTrue(!apoOrder().askLocation)
    }
}
