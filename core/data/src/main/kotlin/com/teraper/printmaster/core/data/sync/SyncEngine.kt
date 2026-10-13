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
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.database.entity.MasterEntity
import com.teraper.printmaster.core.database.entity.CompanyMemberEntity
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.entity.ClientItemEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartEntity
import com.teraper.printmaster.core.database.entity.ClientItemPartValueEntity
import com.teraper.printmaster.core.database.entity.ClientItemValueEntity
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.database.entity.SyncOutboxEntity
import com.teraper.printmaster.core.database.entity.SyncStateEntity
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.LinkDraft
import com.teraper.printmaster.core.model.OrderStatus
import com.teraper.printmaster.core.model.RepairDevice
import com.teraper.printmaster.core.model.SerialNumbers
import com.teraper.printmaster.core.model.SharedChanges
import com.teraper.printmaster.core.model.SharedClient
import com.teraper.printmaster.core.model.SharedContact
import com.teraper.printmaster.core.model.SharedLine
import com.teraper.printmaster.core.model.SharedOrder
import com.teraper.printmaster.core.model.SharedItem
import com.teraper.printmaster.core.model.SharedPart
import com.teraper.printmaster.core.model.SharedField
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.SharedRepair
import com.teraper.printmaster.core.model.SharedPriceItem
import com.teraper.printmaster.core.model.SyncReport
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Sends this phone's changes to the shared spaces and applies what others changed.
 *
 * Every own company that invited masters has a space this phone owns: it sends the orders given
 * to masters attached to that company, their clients and the price list, and applies what the
 * masters finished, took as their own or turned down. Every attached company (another owner's,
 * joined with a code) has a space where this phone is a master: it receives that company's
 * orders for it, their clients and the company's price list, and sends back the result.
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
        companyDao.getProfile() ?: return SyncReport()
        val spaces = companyDao.getSharedCompanies()
        if (spaces.isEmpty()) return SyncReport()
        // Masters who attached since last time get a local record first, so their orders can go out.
        spaces.filter { it.kind == CompanyKind.OWN }.forEach { linkMembers(it) }
        var report = SyncReport(sent = push(spaces))
        for (company in spaces) report += pull(company)
        return report
    }

    // Attached masters

    private suspend fun linkMembers(company: CompanyEntity) {
        val members = backend.members(checkNotNull(company.spaceId))
        db.withTransaction {
            val uids = members.map { it.uid }.toSet()
            for (member in members) {
                val masterId = sync.getMasterByUid(member.uid)?.id ?: companyDao.insertMaster(
                    MasterEntity(name = member.name.ifBlank { member.email }, createdAt = clock.millis(), memberEmail = member.email, memberUid = member.uid),
                )
                companyDao.insertMembership(CompanyMemberEntity(company.id, masterId))
            }
            // Removed from the space: no more orders of this company for them (the master record stays).
            companyDao.getMemberships(company.id).forEach { m ->
                if (sync.getMaster(m.masterId)?.memberUid !in uids) companyDao.deleteMembership(company.id, m.masterId)
            }
        }
    }

    // Sending

    /** Changes for one space, and the outbox entries they came from (put back if sending fails). */
    private class Outgoing {
        val orders = LinkedHashMap<Long, SharedOrder>()
        val clients = LinkedHashMap<Long, SharedClient>()
        val prices = ArrayList<SharedPriceItem>()
        val entries = LinkedHashSet<SyncOutboxEntity>()
        fun changes() = SharedChanges(clients.values.toList(), orders.values.toList(), prices)
    }

    private suspend fun push(spaces: List<CompanyEntity>): Int {
        val byId = spaces.associateBy { it.id }
        val (outgoing, unrouted) = db.withTransaction {
            applying {
                val outbox = sync.getOutbox()
                // Taken out now; put back if sending fails, so a change made meanwhile isn't lost.
                outbox.forEach { sync.deleteOutbox(it.entity, it.rowId) }
                route(outbox, byId) to Unit
            }
        }
        var sent = 0
        var failure: Exception? = null
        for ((companyId, out) in outgoing) {
            val changes = out.changes()
            if (changes.isEmpty) continue
            try {
                backend.push(checkNotNull(byId.getValue(companyId).spaceId), changes)
                sent += changes.clients.size + changes.orders.size + changes.priceItems.size
            } catch (e: Exception) {
                db.withTransaction { out.entries.forEach { reQueue(it) } }
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
        return sent
    }

    private fun reQueue(entry: SyncOutboxEntity) =
        db.openHelper.writableDatabase.execSQL("INSERT OR IGNORE INTO sync_outbox (entity, row_id) VALUES (?, ?)", arrayOf<Any>(entry.entity, entry.rowId))

    /** Which space each change goes to. */
    private suspend fun route(outbox: List<SyncOutboxEntity>, spaces: Map<Long, CompanyEntity>): Map<Long, Outgoing> {
        val out = LinkedHashMap<Long, Outgoing>()
        fun to(companyId: Long, entry: SyncOutboxEntity) = out.getOrPut(companyId) { Outgoing() }.also { it.entries += entry }
        val ownSpaces = spaces.values.filter { it.kind == CompanyKind.OWN }
        val myUid = backend.myUid
        for (entry in outbox) {
            when (entry.entity) {
                ORDER -> {
                    val order = orderDao.getOrder(entry.rowId) ?: continue
                    val company = spaces[order.companyId]
                    val from = order.fromCompanyId?.let { spaces[it] }
                    when {
                        // Done for an attached company: the result goes back to it.
                        company?.kind == CompanyKind.ATTACHED ->
                            to(company.id, entry).orders[order.id] = sharedOrder(order, myUid)
                        // Taken from an attached company as the master's own: it only learns that.
                        from != null && from.kind == CompanyKind.ATTACHED && order.syncId != null ->
                            to(from.id, entry).orders[order.id] = takenOrder(order, myUid)
                        company?.kind == CompanyKind.OWN -> {
                            val master = order.masterId?.let { sync.getMaster(it) }
                            val memberUid = master?.memberUid?.takeIf { sync.isMember(company.id, master.id) > 0 }
                            // Shared before and now given to someone else: the update takes it from the master.
                            if (memberUid == null && order.syncId == null) continue
                            val target = to(company.id, entry)
                            target.orders[order.id] = sharedOrder(order, memberUid)
                            if (memberUid != null) sharedClient(order.clientId, company)?.let { target.clients[order.clientId] = it }
                        }
                    }
                }
                CLIENT -> {
                    val client = clientDao.getClient(entry.rowId) ?: continue
                    val attached = client.attachedCompanyId?.let { spaces[it] }
                    if (attached != null) {
                        // A master may only send clients he added himself.
                        if (clientIsMine(client.id)) sharedClient(client.id, attached)?.let { to(attached.id, entry).clients[client.id] = it }
                    } else if (client.attachedCompanyId == null) {
                        for (companyId in sync.getCompaniesSharingClient(client.id)) {
                            val company = spaces[companyId]?.takeIf { it.kind == CompanyKind.OWN } ?: continue
                            sharedClient(client.id, company)?.let { to(company.id, entry).clients[client.id] = it }
                        }
                    }
                }
                // The own price list goes to every own company's space; an attached company's list is never sent back.
                PRICE -> {
                    val part = sync.getAllParts().firstOrNull { it.id == entry.rowId } ?: continue
                    if (part.attachedCompanyId == null) {
                        val item = catalogPart(part.id) ?: continue
                        ownSpaces.forEach { to(it.id, entry).prices += item }
                    }
                }
            }
        }
        return out
    }

    /** On a master's phone: a client this master added (and so may send). */
    private suspend fun clientIsMine(clientId: Long): Boolean {
        val client = clientDao.getClient(clientId) ?: return false
        return client.syncId == null || client.needsReview
    }

    /** The client as company [company]'s space sees it. */
    private suspend fun sharedClient(clientId: Long, company: CompanyEntity): SharedClient? {
        val isMember = company.kind == CompanyKind.ATTACHED
        val row = sync.getClientWithContacts(clientId) ?: return null
        val client = row.client
        // A client added on a master's phone is marked, so later edits still go out as "from master".
        val createdHere = isMember && (client.syncId == null || client.needsReview)
        if (createdHere && !client.needsReview) sync.setNeedsReview(client.id, true)
        val items = clientItems(clientId).map { owned ->
            val type = owned.type
            SharedItem(
                type = type.name,
                name = owned.item?.name ?: owned.name,
                fields = type.fields.mapNotNull { f -> owned.allValues[f.id]?.let { f.name to it } }.toMap(),
                titleFields = type.titleFields.map { it.name },
                parts = owned.parts.map { part ->
                    SharedPart(
                        type = part.type.name,
                        name = part.item?.title.orEmpty(),
                        fields = part.type.fields.mapNotNull { f -> part.values[f.id]?.let { f.name to it } }.toMap(),
                        serialNumber = part.serialNumber,
                        fieldDefs = part.type.fields.map(::sharedField),
                    )
                },
                place = owned.place,
                id = clientItemSyncId(owned.id),
                serialNumber = owned.serialNumber,
                perClient = type.perClient,
                hasSerial = type.hasSerial,
                fieldDefs = type.fields.map(::sharedField),
            )
        }
        return SharedClient(
            id = client.syncId ?: newId().also { sync.setClientSyncId(client.id, it) },
            name = client.name,
            type = client.type,
            taxId = client.taxId,
            phones = row.phones.sortedBy { it.id }.map { SharedContact(it.number, it.label) },
            addresses = row.addresses.sortedBy { it.id }.map { SharedContact(it.address, it.label, it.mapLink) },
            items = items,
            createdByMaster = createdHere,
            visibleTo = if (isMember) listOfNotNull(backend.myUid) else sync.getMemberUidsOfClient(clientId, company.id),
        )
    }

    private suspend fun sharedOrder(order: OrderEntity, masterUid: String?): SharedOrder {
        val clientSyncId = clientDao.getClient(order.clientId)?.let { it.syncId ?: newId().also { id -> sync.setClientSyncId(it.id, id) } }!!
        val work = repairDao.getRepairs(order.id).map { r ->
            val repair = r.repair
            SharedRepair(
                id = repair.syncId ?: newId().also { sync.setRepairSyncId(repair.id, it) },
                device = deviceName(repair),
                itemId = repair.clientItemId?.let { clientItemSyncId(it) },
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
            // An attached company's order keeps no money here, so the cash flag is on the order.
            paidCash = if (done) order.paidInCash || sync.countCashPayments(order.id) > 0 else null,
            work = work,
            declinedReason = order.declinedReason,
            handedOver = order.handedOver,
            accepted = order.acceptedAt != null,
        )
    }

    /** What the giving company learns of an order the master took as his own: done, nothing more. */
    private fun takenOrder(order: OrderEntity, masterUid: String?) = SharedOrder(
        id = checkNotNull(order.syncId),
        clientId = "",
        masterUid = masterUid,
        scheduledAt = order.scheduledAt,
        description = order.description,
        status = order.status,
        doneAt = order.doneAt,
        takenByMaster = true,
        handedOver = true,
        accepted = true,
        declinedReason = order.declinedReason,
    )

    /** "CF283A · HP LaserJet M125 (Office)", the same text on both phones. */
    private suspend fun deviceName(repair: RepairEntity): String? {
        val row = repair.clientItemId?.let { catalogDao.getClientItem(it) } ?: return null
        val item = catalogWriter.snapshot().clientItem(catalogDao, row) ?: return null
        return RepairDevice(item, item.parts.firstOrNull { it.id == repair.clientItemPartId }).name
    }

    /** The client's items, put together with the catalog. */
    private suspend fun clientItems(clientId: Long): List<ClientItem> {
        val snapshot = catalogWriter.snapshot()
        return catalogDao.getClientItems(clientId).mapNotNull { snapshot.clientItem(catalogDao, it) }
    }

    private suspend fun clientItemSyncId(id: Long): String {
        val row = checkNotNull(catalogDao.getClientItem(id))
        return row.syncId ?: newId().also { sync.setClientItemSyncId(id, it) }
    }

    private suspend fun partSyncId(partId: Long): String? {
        val part = sync.getAllParts().firstOrNull { it.id == partId } ?: return null
        return part.syncId ?: newId().also { sync.setPartSyncId(part.id, it) }
    }

    private suspend fun catalogPart(partId: Long): SharedPriceItem? {
        val part = sync.getAllParts().firstOrNull { it.id == partId } ?: return null
        return SharedPriceItem(
            id = part.syncId ?: newId().also { sync.setPartSyncId(part.id, it) },
            type = part.typeId?.let { id -> catalogDao.getTypes().firstOrNull { it.id == id }?.name },
            name = part.name,
            description = part.description,
            priceMinor = part.priceMinor,
            archived = part.archived,
        )
    }

    // Receiving

    private suspend fun pull(company: CompanyEntity): SyncReport {
        val spaceId = checkNotNull(company.spaceId)
        val isMember = company.kind == CompanyKind.ATTACHED
        val cursorKey = "$CURSOR:$spaceId"
        val (changes, cursor) = backend.pull(spaceId, asOwner = !isMember, cursor = sync.getState(cursorKey))
        val finishedByMaster = ArrayList<Pair<Long, SharedOrder>>()
        var newOrders = 0
        var newClients = 0
        var declined = 0
        var accepted = 0
        val attachedId = company.id.takeIf { isMember }
        db.withTransaction {
            applying {
                if (isMember) changes.priceItems.forEach { applyPrice(it, company.id) }
                changes.clients.forEach { if (applyClient(it, isMember, attachedId)) newClients++ }
                val pending = if (isMember) sync.getOutbox().filter { it.entity == ORDER }.map { it.rowId }.toSet() else emptySet()
                for (order in changes.orders) {
                    val local = sync.getOrderBySyncId(order.id)
                    // An order changed here and not sent yet: this phone's version wins.
                    if (local != null && local.id in pending) continue
                    // The master's answers are about orders this company gave; nothing to create for them.
                    if (!isMember && local == null && (order.takenByMaster || order.declinedReason != null)) continue
                    val id = local?.id ?: insertOrder(order, company.id, isMember)?.also { newOrders++ } ?: continue
                    // The master said yes: the company sees it on the order.
                    if (!isMember && local != null && order.accepted && order.declinedReason == null && local.acceptedAt == null && local.masterId != null) {
                        sync.setAccepted(id, clock.millis())
                        accepted++
                    }
                    when {
                        isMember -> updateMemberOrder(id, order)
                        local?.isOpenOrder() != true -> Unit
                        order.declinedReason != null && local.masterId != null -> {
                            val master = local.masterId?.let { sync.getMaster(it) }
                            sync.declineOrder(id, master?.name.orEmpty(), order.declinedReason.orEmpty())
                            declined++
                        }
                        order.takenByMaster && order.status == OrderStatus.DONE -> {
                            sync.markTakenByMaster(id, order.doneAt ?: clock.millis())
                            finishedByMaster += id to order
                        }
                        order.status == OrderStatus.DONE -> {
                            replaceWork(id, order)
                            finishedByMaster += id to order
                        }
                        order.status == OrderStatus.IN_PROGRESS && local.status == OrderStatus.NEW -> orderDao.setStatus(id, OrderStatus.IN_PROGRESS)
                    }
                }
                cursor?.let { sync.setState(SyncStateEntity(cursorKey, it)) }
            }
        }
        // Charging the client and recording cash is the same as finishing on this phone.
        for ((id, order) in finishedByMaster.filterNot { it.second.takenByMaster }) {
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
            declinedOrders = declined,
            acceptedOrders = accepted,
        )
    }

    private fun OrderEntity.isOpenOrder() = status == OrderStatus.NEW || status == OrderStatus.IN_PROGRESS

    /** True when a client was created here. [attachedCompanyId]: on a master's phone, whose client it is. */
    private suspend fun applyClient(shared: SharedClient, isMember: Boolean, attachedCompanyId: Long?): Boolean {
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
                attachedCompanyId = attachedCompanyId,
            ),
        )
        if (existing != null) {
            clientDao.updateClient(existing.copy(name = shared.name, type = shared.type))
        }
        replaceContacts(id, shared)
        replaceItems(id, shared.items)
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

    /**
     * Items are matched by their own id, so two of one model stay two. A type or field this phone
     * doesn't have yet is made by name; the model is found or added to this phone's catalog.
     */
    private suspend fun replaceItems(clientId: Long, items: List<SharedItem>) {
        val existing = catalogDao.getClientItems(clientId)
        val wanted = items.map { it.id }.toSet()
        // Items added on this phone and never shared (no id) are kept.
        existing.filter { it.syncId != null && it.syncId !in wanted }.forEach { catalogDao.deleteClientItem(it.id) }
        for (item in items) {
            val same = existing.firstOrNull { it.syncId == item.id }
            if (same != null) {
                if (same.place != item.place) sync.setClientItemPlace(same.id, item.place)
                if (same.serialNumber.orEmpty() != item.serialNumber) {
                    sync.setClientItemSerial(same.id, item.serialNumber.ifEmpty { null }, SerialNumbers.key(item.serialNumber).ifEmpty { null })
                }
                continue
            }
            val typeId = catalogWriter.typeId(item.type, hasSerial = item.hasSerial, perClient = item.perClient)
            val values = item.fields.mapKeys { (name, _) -> fieldId(typeId, name, item.fieldDefs, inTitle = name in item.titleFields) }
            val type = checkNotNull(catalogWriter.snapshot().types[typeId])
            // One of a kind (a child) keeps its own values; anything else is a model in this phone's catalog.
            val modelId = if (type.perClient) null else catalogWriter.findOrCreateItem(typeId, catalogWriter.title(type, item.name, values), values, item.name)
            val clientItemId = catalogDao.insertClientItem(
                ClientItemEntity(
                    clientId = clientId, typeId = typeId, itemId = modelId, name = if (type.perClient) item.name else "",
                    place = item.place, syncId = item.id.ifEmpty { null },
                    serialNumber = item.serialNumber.ifEmpty { null }, serialKey = SerialNumbers.key(item.serialNumber).ifEmpty { null },
                ),
            )
            if (type.perClient) catalogDao.insertClientItemValues(values.map { (field, value) -> ClientItemValueEntity(clientItemId, field, value) })
            for (part in item.parts) {
                val partType = catalogWriter.typeId(part.type, parentId = typeId)
                val partItemId = when {
                    part.name.isBlank() -> null
                    modelId != null -> catalogWriter.linkParts(modelId, listOf(LinkDraft(typeId = partType, title = part.name)), exact = false).values.single()
                    else -> catalogWriter.findOrCreateItem(partType, part.name)
                }
                val partId = catalogDao.insertClientItemPart(
                    ClientItemPartEntity(clientItemId = clientItemId, typeId = partType, itemId = partItemId, serialNumber = part.serialNumber.ifEmpty { null }),
                )
                if (partItemId == null) {
                    val partValues = part.fields.mapKeys { (name, _) -> fieldId(partType, name, part.fieldDefs) }
                    catalogDao.insertClientPartValues(partValues.map { (field, value) -> ClientItemPartValueEntity(partId, field, value) })
                }
            }
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
                handedOver = isMember && order.handedOver,
            ),
        )
    }

    /** On a master's phone the company decides the plan: date, text, status. */
    private suspend fun updateMemberOrder(id: Long, order: SharedOrder) {
        val local = orderDao.getOrder(id) ?: return
        sync.updateOrderFields(id, order.status.name, order.doneAt ?: local.doneAt, order.scheduledAt, order.description)
        // Until he answers, the company may still change whether it's given to him.
        if (local.acceptedAt == null && local.handedOver != order.handedOver) sync.setHandedOver(id, order.handedOver)
    }

    /** The work as the master entered it; costs come from this phone's price list. */
    private suspend fun replaceWork(orderId: Long, order: SharedOrder) {
        val local = orderDao.getOrder(orderId) ?: return
        sync.deleteRepairsOf(orderId)
        val items = clientItems(local.clientId)
        for (repair in order.work) {
            val byId = repair.itemId?.let { id -> items.firstOrNull { item -> catalogDao.getClientItem(item.id)?.syncId == id } }
            val device = if (byId != null) {
                RepairDevice(byId, byId.parts.firstOrNull { p -> repair.device?.startsWith("${p.title} · ") == true })
            } else {
                repair.device?.let { name -> devices(items).firstOrNull { it.name == name } }
            }
            val repairId = repairDao.insertRepair(
                RepairEntity(
                    orderId = orderId,
                    clientItemId = device?.item?.id,
                    clientItemPartId = device?.part?.id,
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

    /** The field named [name], made like the sending phone has it when missing here. */
    private suspend fun fieldId(typeId: Long, name: String, defs: List<SharedField>, inTitle: Boolean = false): Long {
        val def = defs.firstOrNull { it.name == name }
        return catalogWriter.fieldId(typeId, name, def?.kind ?: FieldKind.TEXT, def?.options.orEmpty(), def?.inTitle ?: inTitle)
    }

    private fun sharedField(field: ItemField) = SharedField(field.name, field.kind, field.options, field.inTitle)

    /** Every device of the client: each item, and each item with one of its parts. */
    private fun devices(items: List<ClientItem>) = items.flatMap { item -> listOf(RepairDevice(item)) + item.parts.map { RepairDevice(item, it) } }

    /** An attached company's price list item, kept apart from the user's own list. */
    private suspend fun applyPrice(item: SharedPriceItem, attachedCompanyId: Long) {
        val existing = sync.getPartBySyncId(item.id)
        if (existing == null) {
            sync.insertPart(
                RepairPartEntity(
                    typeId = item.type?.let { catalogWriter.typeId(it) }, name = item.name, description = item.description,
                    priceMinor = item.priceMinor, costMinor = 0, archived = item.archived, syncId = item.id,
                    attachedCompanyId = attachedCompanyId,
                ),
            )
        } else {
            sync.updatePart(existing.id, item.type?.let { catalogWriter.typeId(it) }, item.name, item.description, item.priceMinor, item.archived)
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
