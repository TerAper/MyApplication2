package com.teraper.printmaster.core.model

/*
 * What travels between the company's phone and its masters' phones through the shared space.
 * Plain values with global ids, so any store (Firebase, a test fake) can keep them. Money and
 * debts are never in here: a master doesn't see what clients owe.
 */

/** A client as a master needs it: who, how to reach, which items they own. */
data class SharedClient(
    val id: String,
    val name: String,
    val type: ClientType,
    val taxId: String? = null,
    val phones: List<SharedContact> = emptyList(),
    val addresses: List<SharedContact> = emptyList(),
    val items: List<SharedItem> = emptyList(),
    /** Added by a master in the field; the company reviews it. */
    val createdByMaster: Boolean = false,
    /** Masters (account ids) allowed to see this client: those with an order for it. */
    val visibleTo: List<String> = emptyList(),
)

data class SharedContact(val value: String, val label: String = "", val mapLink: String? = null)

/**
 * A client's item. Types travel by name, so the other phone finds or makes the same type;
 * [fields] are values by field name, [titleFields] the ones written before the name ("HP").
 */
data class SharedItem(
    val type: String,
    val name: String,
    val fields: Map<String, String> = emptyMap(),
    val titleFields: List<String> = emptyList(),
    val parts: List<SharedPart> = emptyList(),
    val place: String = "",
    /** The client's item itself: a client can have two of one model. */
    val id: String = "",
    val serialNumber: String = "",
    /** One of a kind (a child): not a catalog model; [fields] are its own values. */
    val perClient: Boolean = false,
    val hasSerial: Boolean = true,
    val hasPhotos: Boolean = true,
    /** How the type's fields are made on the other phone when it doesn't have them yet. */
    val fieldDefs: List<SharedField> = emptyList(),
)

/** A field as the other phone needs to make it: kind and choices, not just the name. */
data class SharedField(val name: String, val kind: FieldKind = FieldKind.TEXT, val options: List<String> = emptyList(), val inTitle: Boolean = false)

/**
 * A part of the client's item: a catalog item by its title ("Cartridge", "CF283A"), or, with an
 * empty [name], what it should be by its [fields] ("Shoe", size 30).
 */
data class SharedPart(
    val type: String,
    val name: String,
    val fields: Map<String, String> = emptyMap(),
    val serialNumber: String = "",
    val fieldDefs: List<SharedField> = emptyList(),
)

/** An order and, once done, the work and how it was paid. */
data class SharedOrder(
    val id: String,
    val clientId: String,
    /** The master it's assigned to (their account id in the shared space). */
    val masterUid: String?,
    val scheduledAt: Long,
    val description: String,
    val address: String? = null,
    val phone: String? = null,
    val status: OrderStatus = OrderStatus.NEW,
    /** Epoch millis when it was finished on the master's phone. */
    val doneAt: Long? = null,
    /** Finished paid in cash (true) or on account (false); null while not finished. */
    val paidCash: Boolean? = null,
    val work: List<SharedRepair> = emptyList(),
    val createdByMaster: Boolean = false,
    /** The master did it as his own order: for the company it's only history (no work, no money). */
    val takenByMaster: Boolean = false,
    /** The master turned it down; the company gives it to someone else. */
    val declinedReason: String? = null,
    /** Given to the master as his own order (company: only history); false = he works for the company. */
    val handedOver: Boolean = false,
    /** The master accepted it. */
    val accepted: Boolean = false,
    /** The company asks the master to share where he is while doing it. */
    val askLocation: Boolean = false,
    /** The master shares it. */
    val sharingLocation: Boolean = false,
)

/** Someone who joined a company's shared space with its code. */
data class SharedMember(val uid: String, val name: String, val email: String)

/** Work on one device: "CF283A · HP M125", or null when no device was chosen. */
data class SharedRepair(
    val id: String,
    val device: String?,
    val note: String,
    val lines: List<SharedLine>,
    /** Which of the client's items ([SharedItem.id]); [device] says which part. */
    val itemId: String? = null,
)

/** A price-list item ([itemId]) or a custom line; prices only, never costs. */
data class SharedLine(val itemId: String?, val name: String, val priceMinor: Long, val quantity: Int)

/** [type] is the item type's name, null = general. */
data class SharedPriceItem(
    val id: String,
    val type: String?,
    val name: String,
    val description: String,
    val priceMinor: Long,
    val archived: Boolean = false,
)

/**
 * One catalog item's stock in the company's warehouse, for its masters. [priceMinor] is the selling
 * price when the company shows prices, else null; costs are never shared. [removed]: gone from the catalog.
 */
data class SharedStockItem(
    val id: String,
    val type: String,
    val name: String,
    val count: Int,
    val priceMinor: Long? = null,
    val removed: Boolean = false,
)

data class SharedChanges(
    val clients: List<SharedClient> = emptyList(),
    val orders: List<SharedOrder> = emptyList(),
    val priceItems: List<SharedPriceItem> = emptyList(),
    val stock: List<SharedStockItem> = emptyList(),
) {
    val isEmpty: Boolean get() = clients.isEmpty() && orders.isEmpty() && priceItems.isEmpty() && stock.isEmpty()
}

/** What one sync did, for notifications ("2 orders finished by masters"). */
data class SyncReport(
    val sent: Int = 0,
    val received: Int = 0,
    /** Company: orders masters finished. Master: new orders sent to them. */
    val newOrders: Int = 0,
    val finishedOrders: Int = 0,
    /** Company: clients masters added, waiting for review. */
    val newClients: Int = 0,
    /** Company: orders masters turned down. */
    val declinedOrders: Int = 0,
    /** Company: orders masters accepted. */
    val acceptedOrders: Int = 0,
) {
    operator fun plus(other: SyncReport) = SyncReport(
        sent + other.sent, received + other.received, newOrders + other.newOrders,
        finishedOrders + other.finishedOrders, newClients + other.newClients, declinedOrders + other.declinedOrders,
        acceptedOrders + other.acceptedOrders,
    )
}
