package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.DeleteOrderResult
import com.teraper.printmaster.core.data.repository.FinishOrderResult
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.data.repository.SavePrinterResult
import com.teraper.printmaster.core.data.repository.SaveRepairResult
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.ClientPrinterDraft
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.RepairLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OfflineRepairsRepositoryTest {

    private val repos = TestRepos()
    private val repairs = repos.repairs
    private var companyId = 0L
    private var clientId = 0L
    private var orderId = 0L
    private var printerId = 0L
    private var cartridgeId = 0L

    private lateinit var refill: PriceItem
    private lateinit var chip: PriceItem

    @Before
    fun setUp() = runTest {
        companyId = repos.register()
        clientId = (repos.clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
        val model = PrinterModelDraft(brand = "HP", name = "M125", cartridges = listOf(CartridgeDraft("CF283A")))
        printerId = (
            repos.printers.savePrinter(
                ClientPrinterDraft(clientId = clientId, model = model, selectedCartridges = model.cartridges.mapTo(mutableSetOf()) { it.key }),
            ) as SavePrinterResult.Saved
            ).printerId
        cartridgeId = repos.printers.getPrinter(printerId)!!.cartridges.single().id
        repos.priceList.saveItem(PriceItemDraft(category = RepairCategory.CARTRIDGE, name = "Refill", priceDigits = "3000", costDigits = "900"))
        repos.priceList.saveItem(PriceItemDraft(category = RepairCategory.CARTRIDGE, name = "Chip", priceDigits = "2500", costDigits = "1000"))
        val items = repos.priceList.observeItems().first()
        refill = items.first { it.name == "Refill" }
        chip = items.first { it.name == "Chip" }
        orderId = (repos.orders.saveOrder(OrderDraft(clientId = clientId, date = LocalDate.of(2026, 10, 7), description = "Refill")) as SaveOrderResult.Saved).orderId
    }

    @After
    fun tearDown() = repos.db.close()

    private suspend fun addWork(draft: RepairDraft = RepairDraft(orderId = orderId).withDevice(printerId, cartridgeId).plus(refill).plus(refill).plus(chip)) =
        repairs.saveRepair(draft)

    @Test
    fun savedRepairHasDeviceLinesAndStartsTheOrder() = runTest {
        val id = (addWork() as SaveRepairResult.Saved).repairId

        val work = repairs.observeOrderWork(orderId).first()
        val repair = work.repairs.single()
        assertEquals(id, repair.id)
        assertEquals("CF283A · HP M125", repair.device!!.name)
        assertEquals(listOf(2, 1), repair.lines.map { it.quantity })
        assertEquals(Money.ofDram(8_500), work.total)
        assertEquals(Money.ofDram(5_700), work.profit)
        assertFalse(work.isBilled)
        assertEquals(OrderStatus.IN_PROGRESS, repos.orders.observeOrder(orderId).first()!!.status)
    }

    @Test
    fun editingReplacesLinesAndKeepsId() = runTest {
        val id = (addWork() as SaveRepairResult.Saved).repairId
        val draft = RepairDraft.from(repairs.observeRepair(id).first()!!).withQuantity(0, 0).withDevice(null)
        assertEquals(SaveRepairResult.Saved(id), repairs.saveRepair(draft))

        val repair = repairs.observeRepair(id).first()!!
        assertNull(repair.device)
        assertEquals(listOf("Chip"), repair.lines.map { it.name })
        assertEquals(1, repos.count("repair_items"))
    }

    @Test
    fun finishingOnAccountChargesTheClient() = runTest {
        addWork()
        repairs.saveRepair(RepairDraft(orderId = orderId).plus(RepairLine(null, "Cleaning", Money.ofDram(1_000))))

        assertEquals(FinishOrderResult.FINISHED, repairs.finishOrder(orderId, paidInCash = false))

        assertEquals(OrderStatus.DONE, repos.orders.observeOrder(orderId).first()!!.status)
        val summary = repos.clients.observeClientSummary(clientId).first()!!
        assertEquals(Money.ofDram(9_500), summary.balance)
        val charges = repos.payments.observeLedger(clientId).first().filterIsInstance<LedgerEntry.Charge>()
        assertEquals(2, charges.size)
        assertTrue(charges.all { it.source == ChargeSource.REPAIR && !it.canDelete })
        assertTrue(charges.any { it.note == "CF283A · HP M125 — Refill ×2, Chip" })

        val work = repairs.observeOrderWork(orderId).first()
        assertTrue(work.isBilled)
        // Finished work is locked until the order is reopened.
        assertEquals(SaveRepairResult.Locked, addWork())
        assertFalse(repairs.deleteRepair(work.repairs.first().id))
        assertEquals(FinishOrderResult.NOT_OPEN, repairs.finishOrder(orderId, paidInCash = true))
    }

    @Test
    fun finishingWithCashLeavesNoDebtAndReopenUndoesIt() = runTest {
        addWork()
        repairs.finishOrder(orderId, paidInCash = true)

        assertEquals(Money.ZERO, repos.clients.observeClientSummary(clientId).first()!!.balance)
        assertEquals(Money.ofDram(8_500), repairs.observeOrderWork(orderId).first().paidCash)
        assertEquals(Money.ofDram(8_500), repos.payments.observeIncome(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7)).first().cash)
        assertEquals(DeleteOrderResult.HAS_RECORDS, repos.orders.deleteOrder(orderId))

        repairs.reopenOrder(orderId)

        assertEquals(0, repos.count("charges"))
        assertEquals(0, repos.count("payments"))
        assertEquals(1, repos.count("repairs"))
        assertEquals(OrderStatus.IN_PROGRESS, repos.orders.observeOrder(orderId).first()!!.status)
        // Unbilled work is deleted together with the order.
        assertEquals(DeleteOrderResult.DELETED, repos.orders.deleteOrder(orderId))
        assertEquals(0, repos.count("repairs"))
    }

    @Test
    fun orderWithoutWorkCantBeFinishedThisWay() = runTest {
        assertEquals(FinishOrderResult.NO_WORK, repairs.finishOrder(orderId, paidInCash = false))
        assertEquals(FinishOrderResult.NOT_FOUND, repairs.finishOrder(999, paidInCash = false))
    }
}
