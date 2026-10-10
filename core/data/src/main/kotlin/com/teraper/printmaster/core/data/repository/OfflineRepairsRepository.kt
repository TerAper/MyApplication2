package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.LedgerDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.RepairDao
import com.teraper.printmaster.core.database.dao.RepairWithItems
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.model.ClientSearch
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.CompanyKind
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
import java.time.Instant
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
    private val analytics: AppAnalytics,
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

    override suspend fun finishOrder(orderId: Long, paidInCash: Boolean, finishedAt: Instant?): FinishOrderResult {
        // Device names for the charge notes, read before the transaction (it only reads them).
        val clientId = orderDao.getOrder(orderId)?.clientId ?: return FinishOrderResult.NOT_FOUND
        val clientPrinters = printers.observeClientPrinters(clientId).first()
        val result = db.withTransaction { finish(orderId, paidInCash, clientPrinters, finishedAt ?: clock.instant()) }
        if (result == FinishOrderResult.FINISHED) analytics.log("order_finished", mapOf("cash" to paidInCash, "from_master" to (finishedAt != null)))
        return result
    }

    private suspend fun finish(orderId: Long, paidInCash: Boolean, clientPrinters: List<ClientPrinter>, finishedAt: Instant): FinishOrderResult {
        val order = orderDao.getOrder(orderId) ?: return FinishOrderResult.NOT_FOUND
        if (!order.isOpen()) return FinishOrderResult.NOT_OPEN
        val repairs = repairDao.getRepairs(orderId).map { it.toModel(clientPrinters) }.filter { it.total.isPositive }
        if (repairs.isEmpty()) return FinishOrderResult.NO_WORK

        val today = finishedAt.atZone(clock.zone).toLocalDate().toEpochDay()
        val now = finishedAt.toEpochMilli()
        // Done for another owner's company: its client owes that company, not this phone's user.
        if (db.companyDao().getCompany(order.companyId)?.kind == CompanyKind.ATTACHED) {
            orderDao.updateOrder(order.copy(status = OrderStatus.DONE, doneAt = now, paidInCash = paidInCash))
            return FinishOrderResult.FINISHED
        }
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
        orderDao.setStatus(orderId, OrderStatus.DONE, doneAt = now)
        return FinishOrderResult.FINISHED
    }

    override suspend fun finishAsMine(orderId: Long, companyId: Long, paidInCash: Boolean): FinishOrderResult {
        val order = orderDao.getOrder(orderId) ?: return FinishOrderResult.NOT_FOUND
        if (!order.isOpen()) return FinishOrderResult.NOT_OPEN
        if (repairDao.getRepairs(orderId).none { r -> r.items.any { it.priceMinor * it.quantity > 0 } }) return FinishOrderResult.NO_WORK
        val company = db.companyDao().getCompany(companyId)
        if (company == null || company.kind != CompanyKind.OWN) return FinishOrderResult.NOT_FOUND
        db.withTransaction { moveToOwnCompany(order, companyId) }
        return finishOrder(orderId, paidInCash)
    }

    /** The order, its client (copied or found among own clients) and its work's devices, under an own company. */
    private suspend fun moveToOwnCompany(order: OrderEntity, companyId: Long) {
        val sync = db.syncDao()
        val clientDao = db.clientDao()
        val catalog = db.catalogDao()
        val source = sync.getClientWithContacts(order.clientId) ?: return
        val own = source.client.taxId?.let { clientDao.findOtherClientWithTaxId(it, 0) }?.takeIf { it.attachedCompanyId == null }
            ?: clientDao.observeClientsWithContacts().first().map { it.client }
                .firstOrNull { ClientSearch.normalizeText(it.name) == ClientSearch.normalizeText(source.client.name) }
        val clientId = own?.id ?: run {
            // The tax ID moves to the own client: that's the one invoices and payments are matched to.
            val taxId = source.client.taxId?.takeIf { clientDao.findOtherClientWithTaxId(it, source.client.id) == null }
            if (taxId != null) clientDao.updateClient(source.client.copy(taxId = null))
            clientDao.insertClient(ClientEntity(name = source.client.name, type = source.client.type, taxId = taxId, createdAt = clock.millis()))
        }
        // Contacts the own client lacks are added, so the order keeps its phone and address.
        val target = checkNotNull(sync.getClientWithContacts(clientId))
        clientDao.upsertPhones(source.phones.filter { p -> target.phones.none { it.number == p.number } }.map { ClientPhoneEntity(clientId = clientId, number = it.number, label = it.label) })
        clientDao.upsertAddresses(
            source.addresses.filter { a -> target.addresses.none { it.address == a.address } }
                .map { ClientAddressEntity(clientId = clientId, address = it.address, label = it.label, mapLink = it.mapLink) },
        )
        val contacts = checkNotNull(sync.getClientWithContacts(clientId))
        val phone = source.phones.firstOrNull { it.id == order.phoneId }?.let { p -> contacts.phones.firstOrNull { it.number == p.number } }
        val address = source.addresses.firstOrNull { it.id == order.addressId }?.let { a -> contacts.addresses.firstOrNull { it.address == a.address } }

        // Printers: the own client's of the same model and place, or copies; the work points at them.
        val ownPrinters = sync.getClientPrinterIds(clientId).mapNotNull { catalog.getClientPrinter(it) }.toMutableList()
        val printerMap = HashMap<Long, Long>()
        val cartridgeMap = HashMap<Long, Long>()
        for (printer in sync.getClientPrinterIds(order.clientId).mapNotNull { catalog.getClientPrinter(it) }) {
            val same = ownPrinters.firstOrNull { it.printer.modelId == printer.printer.modelId && it.printer.location == printer.printer.location }
            val targetId = same?.printer?.id ?: catalog.insertClientPrinter(
                ClientPrinterEntity(clientId = clientId, modelId = printer.printer.modelId, location = printer.printer.location, note = printer.printer.note),
            )
            val existing = same?.cartridges?.map { it.row }.orEmpty()
            val missing = printer.cartridges.map { it.row }.filter { c -> existing.none { it.cartridgeId == c.cartridgeId } }
            catalog.insertClientPrinterCartridges(missing.map { ClientPrinterCartridgeEntity(clientPrinterId = targetId, cartridgeId = it.cartridgeId) })
            val rows = catalog.getClientPrinterCartridges(targetId)
            printerMap[printer.printer.id] = targetId
            printer.cartridges.forEach { c -> rows.firstOrNull { it.cartridgeId == c.row.cartridgeId }?.let { cartridgeMap[c.row.id] = it.id } }
            if (same == null) catalog.getClientPrinter(targetId)?.let { ownPrinters += it }
        }
        repairDao.getRepairs(order.id).forEach { r ->
            val repair = r.repair
            repairDao.updateRepair(
                repair.copy(
                    clientPrinterId = repair.clientPrinterId?.let { printerMap[it] },
                    clientPrinterCartridgeId = repair.clientPrinterCartridgeId?.let { cartridgeMap[it] },
                ),
            )
        }
        orderDao.updateOrder(
            order.copy(
                companyId = companyId,
                clientId = clientId,
                phoneId = phone?.id,
                addressId = address?.id,
                fromCompanyId = order.companyId,
                takenByMaster = true,
            ),
        )
    }

    override suspend fun declineOrder(orderId: Long, reason: String): Boolean {
        val order = orderDao.getOrder(orderId) ?: return false
        if (!order.isOpen() || db.companyDao().getCompany(order.companyId)?.kind != CompanyKind.ATTACHED) return false
        orderDao.updateOrder(order.copy(status = OrderStatus.CANCELLED, declinedReason = reason.trim().ifEmpty { "—" }))
        return true
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
