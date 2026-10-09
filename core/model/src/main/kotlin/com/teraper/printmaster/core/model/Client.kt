package com.teraper.printmaster.core.model

data class ClientPhone(
    val id: Long,
    val number: String,
    val label: String,
)

data class ClientAddress(
    val id: Long,
    val address: String,
    val label: String,
    /** Exact point or map link picked on a map; null = only the text. */
    val mapLink: String? = null,
)

data class Client(
    val id: Long,
    val name: String,
    val type: ClientType,
    /** ՀՎՀՀ (tax ID). Only firms have one. */
    val taxId: String?,
    val note: String,
    val phones: List<ClientPhone>,
    val addresses: List<ClientAddress>,
)

/** A client as shown in lists: contacts plus counts and money totals. */
data class ClientSummary(
    val client: Client,
    val printerCount: Int = 0,
    /** Everything the client was charged: invoices, repairs, manual debts. */
    val charged: Money = Money.ZERO,
    /** Everything the client paid: cash and bank. */
    val paid: Money = Money.ZERO,
    val lastPaymentDate: java.time.LocalDate? = null,
) {
    /** Charges minus payments: positive = owes us, negative = overpaid. */
    val balance: Money get() = charged - paid
}
