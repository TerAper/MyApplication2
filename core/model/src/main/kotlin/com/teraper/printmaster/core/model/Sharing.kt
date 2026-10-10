package com.teraper.printmaster.core.model

/*
 * What travels between the company's phone and its masters' phones through the shared space.
 * Plain values with global ids, so any store (Firebase, a test fake) can keep them. Money and
 * debts are never in here: a master doesn't see what clients owe.
 */

/** A client as a master needs it: who, how to reach, which printers. */
data class SharedClient(
    val id: String,
    val name: String,
    val type: ClientType,
    val taxId: String? = null,
    val phones: List<SharedContact> = emptyList(),
    val addresses: List<SharedContact> = emptyList(),
    val printers: List<SharedPrinter> = emptyList(),
    /** Added by a master in the field; the company reviews it. */
    val createdByMaster: Boolean = false,
    /** Masters (account ids) allowed to see this client: those with an order for it. */
    val visibleTo: List<String> = emptyList(),
)

data class SharedContact(val value: String, val label: String = "", val mapLink: String? = null)

data class SharedPrinter(
    val brand: String,
    val model: String,
    val printType: PrintType,
    val colorType: ColorType,
    val cartridges: List<String> = emptyList(),
    val location: String = "",
    /** The client's printer itself: a client can have two of one model. */
    val id: String = "",
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
)

/** Someone who joined a company's shared space with its code. */
data class SharedMember(val uid: String, val name: String, val email: String)

/** Work on one device: "CF283A · HP M125", or null when no device was chosen. */
data class SharedRepair(
    val id: String,
    val device: String?,
    val note: String,
    val lines: List<SharedLine>,
    /** Which of the client's printers ([SharedPrinter.id]); [device] says which cartridge. */
    val printerId: String? = null,
)

/** A price-list item ([itemId]) or a custom line; prices only, never costs. */
data class SharedLine(val itemId: String?, val name: String, val priceMinor: Long, val quantity: Int)

data class SharedPriceItem(
    val id: String,
    val category: RepairCategory,
    val name: String,
    val description: String,
    val priceMinor: Long,
    val archived: Boolean = false,
)

data class SharedChanges(
    val clients: List<SharedClient> = emptyList(),
    val orders: List<SharedOrder> = emptyList(),
    val priceItems: List<SharedPriceItem> = emptyList(),
) {
    val isEmpty: Boolean get() = clients.isEmpty() && orders.isEmpty() && priceItems.isEmpty()
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
) {
    operator fun plus(other: SyncReport) = SyncReport(
        sent + other.sent, received + other.received, newOrders + other.newOrders,
        finishedOrders + other.finishedOrders, newClients + other.newClients, declinedOrders + other.declinedOrders,
    )
}
