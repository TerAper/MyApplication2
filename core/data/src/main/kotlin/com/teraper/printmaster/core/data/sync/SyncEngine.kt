package com.teraper.printmaster.core.data.sync

import androidx.room.withTransaction
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.RepairDao
import com.teraper.printmaster.core.database.dao.SyncDao
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.database.entity.SyncOutboxEntity
import com.teraper.printmaster.core.database.entity.SyncStateEntity
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.PrinterModelDraft
import com.teraper.printmaster.core.model.SharedChanges
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedContact
import com.teraper.printmaster.core.model.SharedLine
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedPrinter
import com.teraper.printmaster.core.model.SharedRepair
import com.teraper.printmaster.core.model.SharedPriceItem
import com.teraper.printmaster.core.model.SyncReport
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Sends this phone's changes to the shared space and applies what others changed.
 *
 * The company's phone (COMPANY or MASTER mode) is the owner: it sends orders assigned to masters
 * who joined, their clients and the price list, and applies what masters finished or added.
 * A master's phone (JOINED) receives those and sends back finished work, new orders and clients.
 */
class SyncEngine @Inject internal constructor(
    private val db: PrintMasterDatabase,
    private val sync: SyncDao,
    private val clientDao: ClientDao,
    private val orderDao: OrderDao,
    private val repairDao: RepairDao,
    private val catalogDao: CatalogDao,
    private val catalogWriter: CatalogWriter,
    private val companyDao: CompanyDao,
    private val repairs: RepairsRepository,
    private val backend: SyncBackend,
    private val clock: Clock,
) {

    suspend fun sync(): SyncReport {
        backend.myUid ?: return SyncReport()
        val profile = companyDao.getProfile() ?: return SyncReport()
        val isMember = profile.mode == AccountMode.JOINED
        val sent = push(isMember)
        return pull(isMember, companyId = profile.defaultCompanyId).copy(sent = sent)
    }

    // Sending

    private suspend fun push(isMember: Boolean): Int {
        val (changes, taken) = db.withTransaction {
            applying {
                val outbox = sync.getOutbox()
                // Taken out now; put back if sending fails, so a change made meanwhile isn't lost.
                outbox.forEach { sync.deleteOutbox(it.entity, it.rowId) }
                collect(outbox, isMember) to outbox
            }
        }
        if (changes.isEmpty) return 0
        try {
            backend.push(changes)
        } catch (e: Exception) {
            db.withTransaction { taken.forEach { reQueue(it) } }
            throw e
        }
        return changes.clients.size + changes.orders.size + changes.priceItems.size
    }

    private fun reQueue(entry: SyncOutboxEntity) =
        db.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO sync_outbox (entity, row_id) VALUES (?, ?)", arrayOf<Any>(entry.entity, entry.rowId))

    private suspend fun collect(outbox: List<SyncOutboxEntity>, isMember: Boolean): SharedChanges {
        val orders = LinkedHashMap<Long, SharedOrder>()
        val clients = LinkedHashMap<Long, SharedClient>()
        val prices = ArrayList<SharedPriceItem>()
        val myUid = backend.myUid
        for (entry in outbox) {
            when (entry.entity) {
                ORDER -> {
                    val order = orderDao.getOrder(entry.rowId) ?: continue
                    val masterUid = if (isMember) myUid else order.masterId?.let { sync.getMaster(it) }?.memberUid
                    // The company only shares orders of masters who joined.
                    if (masterUid == null) continue
                    orders[order.id] = sharedOrder(order, masterUid)
                    if (!isMember || clientIsMine(order.clientId)) clients.getOrPut(order.clientId) { sharedClient(order.clientId, isMember) ?: continue }
                }
                CLIENT -> when {
                    isMember && !clientIsMine(entry.rowId) && sync.getClientWithContacts(entry.rowId)?.client?.syncId != null -> Unit
                    isMember -> sharedClient(entry.rowId, isMember = true)?.let { clients[entry.rowId] = it }
                    sync.countSharedOrders(entry.rowId) > 0 -> sharedClient(entry.rowId, isMember = false)?.let { clients[entry.rowId] = it }
                }
                PRICE -> if (!isMember) catalogPart(entry.rowId)?.let { prices += it }
            }
        }
        return SharedChanges(clients.values.toList(), orders.values.toList(), prices)
    }

    /** On a master's phone: a client this master added (and so may send). */
    private suspend fun clientIsMine(clientId: Long): Boolean {
        val client = clientDao.getClient(clientId) ?: return false
        return client.syncId == null || client.needsReview
    }

    private suspend fun sharedClient(clientId: Long, isMember: Boolean): SharedClient? {
        val row = sync.getClientWithContacts(clientId) ?: return null
        val client = row.client
        // A client added on a master's phone is marked, so later edits still go out as "from master".
        val createdHere = isMember && (client.syncId == null || client.needsReview)
        if (createdHere && !client.needsReview) sync.setNeedsReview(client.id, true)
        val printers = sync.getClientPrinterIds(clientId).mapNotNull { catalogDao.getClientPrinter(it) }.map { p ->
            SharedPrinter(
                brand = p.model.brand.name,
                model = p.model.model.name,
                printType = p.model.model.printType,
                colorType = p.model.model.colorType,
                cartridges = p.cartridges.map { it.cartridge.cartridge.name },
                location = p.printer.location,
                id = p.printer.syncId ?: newId().also { sync.setClientPrinterSyncId(p.printer.id, it) },
            )
        }
        return SharedClient(
            id = client.syncId ?: newId().also { sync.setClientSyncId(client.id, it) },
            name = client.name,
            type = client.type,
            taxId = client.taxId,
            phones = row.phones.sortedBy { it.id }.map { SharedContact(it.number, it.label) },
            addresses = row.addresses.sortedBy { it.id }.map { SharedContact(it.address, it.label, it.mapLink) },
            printers = printers,
            createdByMaster = createdHere,
            visibleTo = if (isMember) listOfNotNull(backend.myUid) else sync.getMemberUidsOfClient(clientId),
        )
    }

    private suspend fun sharedOrder(order: OrderEntity, masterUid: String): SharedOrder {
        val clientSyncId = clientDao.getClient(order.clientId)?.let { it.syncId ?: newId().also { id -> sync.setClientSyncId(it.id, id) } }!!
        val work = repairDao.getRepairs(order.id).map { r ->
            val repair = r.repair
            SharedRepair(
                id = repair.syncId ?: newId().also { sync.setRepairSyncId(repair.id, it) },
                device = deviceName(repair),
                printerId = repair.clientPrinterId?.let { id ->
                    catalogDao.getClientPrinter(id)?.printer?.let { it.syncId ?: newId().also { new -> sync.setClientPrinterSyncId(it.id, new) } }
                },
                note = repair.note,
                lines = r.items.map { item ->
                    SharedLine(item.partId?.let { partSyncId(it) }, item.name, item.priceMinor, item.quantity)
                },
            )
        }
        val done = order.status == OrderStatus.DONE
        return SharedOrder(
            id = order.syncId ?: newId().also { sync.setOrderSyncId(order.id, it) },
            clientId = clientSyncId,
            masterUid = masterUid,
            scheduledAt = order.scheduledAt,
            description = order.description,
            address = order.addressId?.let { sync.getAddressText(it) },
            phone = order.phoneId?.let { sync.getPhoneText(it) },
            status = order.status,
            doneAt = order.doneAt,
            paidCash = if (done) sync.countCashPayments(order.id) > 0 else null,
            work = work,
        )
    }

    /** "CF283A · HP LaserJet M125 (Office)", the same text on both phones. */
    private suspend fun deviceName(repair: RepairEntity): String? {
        val printer = repair.clientPrinterId?.let { catalogDao.getClientPrinter(it) } ?: return null
        val model = CatalogNames.clean("${printer.model.brand.name} ${printer.model.model.name}") +
            if (printer.printer.location.isBlank()) "" else " (${printer.printer.location})"
        val cartridge = printer.cartridges.firstOrNull { it.row.id == repair.clientPrinterCartridgeId }?.cartridge?.cartridge?.name
        return cartridge?.let { "$it · $model" } ?: model
    }

    private suspend fun partSyncId(partId: Long): String? {
        val part = sync.getAllParts().firstOrNull { it.id == partId } ?: return null
        return part.syncId ?: newId().also { sync.setPartSyncId(part.id, it) }
    }

    private suspend fun catalogPart(partId: Long): SharedPriceItem? {
        val part = sync.getAllParts().firstOrNull { it.id == partId } ?: return null
        return SharedPriceItem(
            id = part.syncId ?: newId().also { sync.setPartSyncId(part.id, it) },
            category = part.category,
            name = part.name,
            description = part.description,
            priceMinor = part.priceMinor,
            archived = part.archived,
        )
    }

    // Receiving

    private suspend fun pull(isMember: Boolean, companyId: Long): SyncReport {
        val (changes, cursor) = backend.pull(sync.getState(CURSOR))
        val finishedByMaster = ArrayList<Pair<Long, SharedOrder>>()
        var newOrders = 0
        var newClients = 0
        db.withTransaction {
            applying {
                if (isMember) changes.priceItems.forEach { applyPrice(it) }
                changes.clients.forEach { if (applyClient(it, isMember)) newClients++ }
                val pending = if (isMember) sync.getOutbox().filter { it.entity == ORDER }.map { it.rowId }.toSet() else emptySet()
                for (order in changes.orders) {
                    val local = sync.getOrderBySyncId(order.id)
                    // An order changed here and not sent yet: this phone's version wins.
                    if (local != null && local.id in pending) continue
                    val id = local?.id ?: insertOrder(order, companyId, isMember)?.also { newOrders++ } ?: continue
                    if (isMember) {
                        updateMemberOrder(id, order)
                    } else if (order.status == OrderStatus.DONE && sync.getOrderBySyncId(order.id)?.status?.isOpen() == true) {
                        replaceWork(id, order)
                        finishedByMaster += id to order
                    } else if (order.status == OrderStatus.IN_PROGRESS && local?.status == OrderStatus.NEW) {
                        orderDao.setStatus(id, OrderStatus.IN_PROGRESS)
                    }
                }
                cursor?.let { sync.setState(SyncStateEntity(CURSOR, it)) }
            }
        }
        // Charging the client and recording cash is the same as finishing on this phone.
        for ((id, order) in finishedByMaster) {
            setApplying(true)
            try {
                repairs.finishOrder(id, paidInCash = order.paidCash == true, finishedAt = order.doneAt?.let(Instant::ofEpochMilli))
            } finally {
                setApplying(false)
            }
        }
        return SyncReport(
            received = changes.clients.size + changes.orders.size + changes.priceItems.size,
            newOrders = newOrders,
            finishedOrders = finishedByMaster.size,
            newClients = newClients,
        )
    }

    private fun OrderStatus.isOpen() = this == OrderStatus.NEW || this == OrderStatus.IN_PROGRESS

    /** True when a client was created here. */
    private suspend fun applyClient(shared: SharedClient, isMember: Boolean): Boolean {
        val existing = sync.getClientBySyncId(shared.id)
        // The company owns its clients: only clients from masters still under review are updated.
        if (!isMember && existing != null && !existing.needsReview) return false
        val id = existing?.id ?: clientDao.insertClient(
            ClientEntity(
                name = shared.name,
                type = shared.type,
                taxId = shared.taxId?.takeIf { clientDao.findOtherClientWithTaxId(it, 0) == null },
                createdAt = clock.millis(),
                syncId = shared.id,
                needsReview = !isMember && shared.createdByMaster,
            ),
        )
        if (existing != null) {
            clientDao.updateClient(existing.copy(name = shared.name, type = shared.type))
        }
        replaceContacts(id, shared)
        replacePrinters(id, shared.printers)
        return existing == null
    }

    private suspend fun replaceContacts(clientId: Long, shared: SharedClient) {
        val row = sync.getClientWithContacts(clientId) ?: return
        // Rows with the same text keep their id, so orders pointing at them stay linked.
        val keepPhones = row.phones.filter { p -> shared.phones.any { it.value == p.number } }
        clientDao.deletePhonesExcept(clientId, keepPhones.map { it.id })
        clientDao.upsertPhones(
            shared.phones.map { c -> ClientPhoneEntity(keepPhones.firstOrNull { it.number == c.value }?.id ?: 0, clientId, c.value, c.label) },
        )
        val keepAddresses = row.addresses.filter { a -> shared.addresses.any { it.value == a.address } }
        clientDao.deleteAddressesExcept(clientId, keepAddresses.map { it.id })
        clientDao.upsertAddresses(
            shared.addresses.map { c ->
                ClientAddressEntity(keepAddresses.firstOrNull { it.address == c.value }?.id ?: 0, clientId, c.value, c.label, c.mapLink)
            },
        )
    }

    /** Printers are matched by their own id, so two printers of one model stay two. */
    private suspend fun replacePrinters(clientId: Long, printers: List<SharedPrinter>) {
        val existing = sync.getClientPrinterIds(clientId).mapNotNull { catalogDao.getClientPrinter(it) }
        val wanted = printers.map { it.id }.toSet()
        // Printers added on this phone and never shared (no id) are kept.
        existing.filter { it.printer.syncId != null && it.printer.syncId !in wanted }.forEach { catalogDao.deleteClientPrinter(it.printer.id) }
        for (printer in printers) {
            val same = existing.firstOrNull { it.printer.syncId == printer.id }
            if (same != null) {
                if (same.printer.location != printer.location) sync.setClientPrinterLocation(same.printer.id, printer.location)
                continue
            }
            val cartridges = printer.cartridges.map { CartridgeDraft(it) }
            val modelId = catalogWriter.findOrCreateModel(PrinterModelDraft(brand = printer.brand, name = printer.model, printType = printer.printType, colorType = printer.colorType))
            val cartridgeIds = catalogWriter.linkCartridges(modelId, cartridges, exact = false)
            val printerId = catalogDao.insertClientPrinter(
                ClientPrinterEntity(clientId = clientId, modelId = modelId, location = printer.location, syncId = printer.id.ifEmpty { null }),
            )
            catalogDao.insertClientPrinterCartridges(cartridgeIds.values.map { ClientPrinterCartridgeEntity(clientPrinterId = printerId, cartridgeId = it) })
        }
    }

    private suspend fun insertOrder(order: SharedOrder, companyId: Long, isMember: Boolean): Long? {
        val client = sync.getClientBySyncId(order.clientId) ?: return null
        val masterId = if (isMember) null else order.masterUid?.let { sync.getMasterByUid(it) }?.id
        val contacts = sync.getClientWithContacts(client.id)
        return orderDao.insertOrder(
            OrderEntity(
                companyId = companyId,
                masterId = masterId,
                clientId = client.id,
                scheduledAt = order.scheduledAt,
                addressId = contacts?.addresses?.firstOrNull { it.address == order.address }?.id,
                phoneId = contacts?.phones?.firstOrNull { it.number == order.phone }?.id,
                description = order.description,
                createdAt = clock.millis(),
                syncId = order.id,
            ),
        )
    }

    /** On a master's phone the company decides the plan: date, text, status. */
    private suspend fun updateMemberOrder(id: Long, order: SharedOrder) {
        val local = orderDao.getOrder(id) ?: return
        sync.updateOrderFields(id, order.status.name, order.doneAt ?: local.doneAt, order.scheduledAt, order.description)
    }

    /** The work as the master entered it; costs come from this phone's price list. */
    private suspend fun replaceWork(orderId: Long, order: SharedOrder) {
        val local = orderDao.getOrder(orderId) ?: return
        sync.deleteRepairsOf(orderId)
        val printers = sync.getClientPrinterIds(local.clientId).mapNotNull { catalogDao.getClientPrinter(it) }
        for (repair in order.work) {
            val byId = repair.printerId?.let { id -> printers.firstOrNull { it.printer.syncId == id } }
            val device = if (byId != null) {
                val cartridgeId = byId.cartridges.firstOrNull { c -> repair.device?.startsWith("${c.cartridge.cartridge.name} · ") == true }?.row?.id
                Triple(repair.device.orEmpty(), byId.printer.id, cartridgeId)
            } else {
                repair.device?.let { name -> devices(printers).firstOrNull { it.first == name } }
            }
            val repairId = repairDao.insertRepair(
                RepairEntity(
                    orderId = orderId,
                    clientPrinterId = device?.second,
                    clientPrinterCartridgeId = device?.third,
                    note = repair.note,
                    createdAt = clock.millis(),
                    syncId = repair.id,
                ),
            )
            repairDao.insertItems(
                repair.lines.map { line ->
                    val part = line.itemId?.let { sync.getPartBySyncId(it) }
                    RepairItemEntity(repairId = repairId, partId = part?.id, name = line.name, priceMinor = line.priceMinor, costMinor = part?.costMinor ?: 0, quantity = line.quantity)
                },
            )
        }
    }

    /** Every device name of the client's printers → (printer id, cartridge id). */
    private fun devices(printers: List<com.teraper.printmaster.core.database.model.ClientPrinterWithDetails>) = printers.flatMap { p ->
        val model = CatalogNames.clean("${p.model.brand.name} ${p.model.model.name}") +
            if (p.printer.location.isBlank()) "" else " (${p.printer.location})"
        listOf(Triple(model, p.printer.id, null as Long?)) + p.cartridges.map { Triple("${it.cartridge.cartridge.name} · $model", p.printer.id, it.row.id) }
    }

    private suspend fun applyPrice(item: SharedPriceItem) {
        val existing = sync.getPartBySyncId(item.id)
        if (existing == null) {
            sync.insertPart(
                RepairPartEntity(
                    category = item.category, name = item.name, description = item.description,
                    priceMinor = item.priceMinor, costMinor = 0, archived = item.archived, syncId = item.id,
                ),
            )
        } else {
            sync.updatePart(existing.id, item.category.name, item.name, item.description, item.priceMinor, item.archived)
        }
    }

    // Helpers

    /** Runs [block] with the outbox triggers off, so received changes aren't sent back. */
    private suspend fun <T> applying(block: suspend () -> T): T {
        setApplying(true)
        try {
            return block()
        } finally {
            setApplying(false)
        }
    }

    private suspend fun setApplying(on: Boolean) = sync.setState(SyncStateEntity(APPLYING, if (on) "1" else "0"))

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val ORDER = "order"
        const val CLIENT = "client"
        const val PRICE = "price"
        const val CURSOR = "cursor"
        const val APPLYING = "applying"
    }
}
