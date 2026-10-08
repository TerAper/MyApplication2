package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.LedgerDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.RepairDao
import com.teraper.printmaster.core.database.dao.RepairWithItems
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientPrinter
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.Repair
import com.teraper.printmaster.core.model.RepairDevice
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.RepairLine
import com.teraper.printmaster.core.model.sum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineRepairsRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val repairDao: RepairDao,
    private val orderDao: OrderDao,
    private val ledgerDao: LedgerDao,
    private val printers: PrintersRepository,
    private val clock: Clock,
) : RepairsRepository {

    /** The client's printers, to show which device each repair was on. */
    private fun printersOfOrder(orderId: Long): Flow<List<ClientPrinter>> =
        orderDao.observeOrder(orderId).map { it?.order?.clientId }.distinctUntilChanged().flatMapLatest { clientId ->
            if (clientId == null) flowOf(emptyList()) else printers.observeClientPrinters(clientId)
        }

    override fun observeOrderWork(orderId: Long): Flow<OrderWork> = combine(
        repairDao.observeRepairs(orderId),
        printersOfOrder(orderId),
        repairDao.observeChargeCount(orderId),
        repairDao.observeCashPaid(orderId),
    ) { repairs, printers, charges, cash ->
        OrderWork(repairs.map { it.toModel(printers) }, isBilled = charges > 0, paidCash = Money(cash))
    }

    override fun observeRepair(id: Long): Flow<Repair?> =
        repairDao.observeRepair(id).flatMapLatest { row ->
            if (row == null) {
                flowOf(null)
            } else {
                printersOfOrder(row.repair.orderId).map { row.toModel(it) }
            }
        }

    override suspend fun saveRepair(draft: RepairDraft): SaveRepairResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveRepairResult.Invalid(errors)
        return db.withTransaction {
            val order = orderDao.getOrder(draft.orderId)
            if (order == null || !order.isOpen() || repairDao.countCharges(order.id) > 0) {
                return@withTransaction SaveRepairResult.Locked
            }
            // A device must be one of this client's printers; anything else is dropped.
            val printer = draft.printerId?.let { printers.getPrinter(it) }?.takeIf { it.clientId == order.clientId }
            val cartridgeId = draft.cartridgeId?.takeIf { id -> printer?.cartridges?.any { it.id == id } == true }
            val repairId = if (draft.isNew) {
                repairDao.insertRepair(
                    RepairEntity(
                        orderId = order.id,
                        clientPrinterId = printer?.id,
                        clientPrinterCartridgeId = cartridgeId,
                        note = draft.note.trim(),
                        createdAt = clock.millis(),
                    ),
                )
            } else {
                val existing = repairDao.getRepair(draft.id)
                if (existing == null || existing.orderId != order.id) return@withTransaction SaveRepairResult.Locked
                repairDao.updateRepair(existing.copy(clientPrinterId = printer?.id, clientPrinterCartridgeId = cartridgeId, note = draft.note.trim()))
                repairDao.deleteItems(existing.id)
                existing.id
            }
            repairDao.insertItems(
                draft.lines.map { RepairItemEntity(repairId = repairId, partId = it.partId, name = it.name.trim(), priceMinor = it.price.minor, costMinor = it.cost.minor, quantity = it.quantity) },
            )
            if (order.status == OrderStatus.NEW) orderDao.setStatus(order.id, OrderStatus.IN_PROGRESS)
            SaveRepairResult.Saved(repairId)
        }
    }

    override suspend fun deleteRepair(id: Long): Boolean = db.withTransaction {
        val repair = repairDao.getRepair(id) ?: return@withTransaction false
        val order = orderDao.getOrder(repair.orderId)
        if (order == null || !order.isOpen() || repairDao.countCharges(order.id) > 0) return@withTransaction false
        repairDao.deleteRepair(id) > 0
    }

    override suspend fun finishOrder(orderId: Long, paidInCash: Boolean): FinishOrderResult {
        // Device names for the charge notes, read before the transaction (it only reads them).
        val clientId = orderDao.getOrder(orderId)?.clientId ?: return FinishOrderResult.NOT_FOUND
        val clientPrinters = printers.observeClientPrinters(clientId).first()
        return db.withTransaction { finish(orderId, paidInCash, clientPrinters) }
    }

    private suspend fun finish(orderId: Long, paidInCash: Boolean, clientPrinters: List<ClientPrinter>): FinishOrderResult {
        val order = orderDao.getOrder(orderId) ?: return FinishOrderResult.NOT_FOUND
        if (!order.isOpen()) return FinishOrderResult.NOT_OPEN
        val repairs = repairDao.getRepairs(orderId).map { it.toModel(clientPrinters) }.filter { it.total.isPositive }
        if (repairs.isEmpty()) return FinishOrderResult.NO_WORK

        val today = LocalDate.now(clock).toEpochDay()
        val now = clock.millis()
        repairs.forEach { repair ->
            ledgerDao.insertCharge(
                ChargeEntity(
                    companyId = order.companyId,
                    clientId = order.clientId,
                    source = ChargeSource.REPAIR,
                    documentNumber = null,
                    amountMinor = repair.total.minor,
                    dateEpochDay = today,
                    rawName = null,
                    rawTaxId = null,
                    repairId = repair.id,
                    importBatchId = null,
                    note = repair.chargeNote(),
                    createdAt = now,
                ),
            )
        }
        if (paidInCash) {
            ledgerDao.insertPayment(
                PaymentEntity(
                    companyId = order.companyId,
                    clientId = order.clientId,
                    method = PaymentMethod.CASH,
                    amountMinor = repairs.map { it.total }.sum().minor,
                    dateEpochDay = today,
                    reference = null,
                    rawPayerName = null,
                    orderId = orderId,
                    importBatchId = null,
                    note = order.description,
                    // Just after the charges, so the payment is listed above them.
                    createdAt = now + 1,
                ),
            )
        }
        orderDao.setStatus(orderId, OrderStatus.DONE)
        return FinishOrderResult.FINISHED
    }

    override suspend fun reopenOrder(orderId: Long) {
        db.withTransaction {
            repairDao.deleteCharges(orderId)
            repairDao.deleteCashPayments(orderId)
            orderDao.setStatus(orderId, OrderStatus.IN_PROGRESS)
        }
    }
}

private fun OrderEntity.isOpen() = status == OrderStatus.NEW || status == OrderStatus.IN_PROGRESS

private fun RepairWithItems.toModel(printers: List<ClientPrinter>): Repair {
    val printer = printers.firstOrNull { it.id == repair.clientPrinterId }
    return Repair(
        id = repair.id,
        orderId = repair.orderId,
        device = printer?.let { p -> RepairDevice(p, p.cartridges.firstOrNull { it.id == repair.clientPrinterCartridgeId }) },
        note = repair.note,
        lines = items.sortedBy { it.id }.map { RepairLine(it.partId, it.name, Money(it.priceMinor), Money(it.costMinor), it.quantity) },
    )
}

/** What the client sees in their history: "CF283A · HP M125 — Refill ×2, Chip". */
private fun Repair.chargeNote(): String {
    val work = lines.joinToString(", ") { if (it.quantity > 1) "${it.name} ×${it.quantity}" else it.name }
    return listOfNotNull(device?.name, work).joinToString(" — ")
}
