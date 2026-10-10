package com.teraper.printmaster.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A planned visit to a client, done for one company. */
data class Order(
    val id: Long,
    val companyId: Long,
    val clientId: Long,
    val clientName: String,
    val scheduledAt: LocalDateTime,
    val description: String,
    val status: OrderStatus = OrderStatus.NEW,
    val addressId: Long? = null,
    val address: String? = null,
    /** Exact point of the address, when it was picked on a map. */
    val addressLink: String? = null,
    val phoneId: Long? = null,
    val phone: String? = null,
    val masterId: Long? = null,
    val masterName: String? = null,
    /** When it was finished; null while open. */
    val doneAt: LocalDateTime? = null,
    /** Another owner's company gave this order to the user (an attached master): no money is kept for it here. */
    val fromAttachedCompany: Boolean = false,
    val companyName: String = "",
    /** ATTACHED: who owns the company that gave it. */
    val companyOwner: String = "",
    /** The attached master counts the order as his own; for the giving company it's only history. */
    val takenByMaster: Boolean = false,
    /** Master's phone: the attached company he took this order from as his own. */
    val takenFrom: String? = null,
    /** The attached master who turned it down, and why (owner's phone). */
    val declinedBy: String? = null,
    val declinedReason: String? = null,
) {
    val date: LocalDate get() = scheduledAt.toLocalDate()

    /** Still to be done (not finished or cancelled). */
    val isOpen: Boolean get() = status == OrderStatus.NEW || status == OrderStatus.IN_PROGRESS
}

/** The new/edit order form. [id] 0 = new. */
data class OrderDraft(
    val id: Long = 0,
    val clientId: Long? = null,
    val date: LocalDate,
    val time: LocalTime = DEFAULT_TIME,
    val addressId: Long? = null,
    val phoneId: Long? = null,
    val masterId: Long? = null,
    val description: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val scheduledAt: LocalDateTime get() = date.atTime(time)

    fun validate(): Set<OrderDraftError> = buildSet {
        if (clientId == null) add(OrderDraftError.CLIENT_REQUIRED)
        if (description.isBlank()) add(OrderDraftError.DESCRIPTION_REQUIRED)
    }

    /**
     * A newly picked client brings their first address and phone; ones from
     * the previous client no longer apply.
     */
    fun withClient(client: Client): OrderDraft = copy(
        clientId = client.id,
        addressId = client.addresses.firstOrNull()?.id,
        phoneId = client.phones.firstOrNull()?.id,
    )

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(10, 0)

        /** The quick time choices on the form; any other time goes through a picker. */
        val QUICK_TIMES: List<LocalTime> = listOf(9, 10, 11, 12, 14, 15, 16).map { LocalTime.of(it, 0) }

        fun from(order: Order) = OrderDraft(
            id = order.id,
            clientId = order.clientId,
            date = order.date,
            time = order.scheduledAt.toLocalTime(),
            addressId = order.addressId,
            phoneId = order.phoneId,
            masterId = order.masterId,
            description = order.description,
        )
    }
}

enum class OrderDraftError { CLIENT_REQUIRED, DESCRIPTION_REQUIRED }

/** Which status changes the order screen offers. */
fun OrderStatus.nextActions(): List<OrderStatus> = when (this) {
    OrderStatus.NEW -> listOf(OrderStatus.IN_PROGRESS, OrderStatus.DONE, OrderStatus.CANCELLED)
    OrderStatus.IN_PROGRESS -> listOf(OrderStatus.DONE, OrderStatus.CANCELLED)
    OrderStatus.DONE, OrderStatus.CANCELLED -> listOf(OrderStatus.NEW)
}
