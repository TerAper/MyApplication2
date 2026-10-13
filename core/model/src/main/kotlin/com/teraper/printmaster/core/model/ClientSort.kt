package com.teraper.printmaster.core.model

import java.text.Collator
import java.util.Locale

/**
 * How the client list is ordered. Every order falls back to the name, so clients that are equal
 * by the chosen measure (no debt, never visited) still come in a stable, readable order.
 * [needsMoney]: orders by money, not offered where money is hidden (a master's phone).
 */
enum class ClientSort(val needsMoney: Boolean = false) {
    /** A to Я, Armenian alphabet; quotes and legal forms ignored: «ԱԲԳ Սերվիս» ՍՊԸ is under Ա. */
    NAME,

    /** Who owes the most first. */
    DEBT(needsMoney = true),

    /** Who to call first: debtors who paid longest ago (or never) first, the bigger debt first among them. */
    LONGEST_UNPAID(needsMoney = true),

    /** Most recent payment first; never paid last. */
    LAST_PAYMENT(needsMoney = true),

    /** Most recent visit (order) first; never visited last. */
    LAST_ORDER,

    /** Most orders first. */
    MOST_ORDERS,

    /** Clients added most recently first. */
    NEWEST,

    /** Most items (printers, children…) first. */
    MOST_ITEMS,
    ;

    fun sort(clients: List<ClientSummary>): List<ClientSummary> {
        val byName = ClientNames.comparator()
        val first: Comparator<ClientSummary> = when (this) {
            NAME -> Comparator { _, _ -> 0 }
            DEBT -> compareByDescending { it.balance.minor }
            LONGEST_UNPAID -> compareByDescending<ClientSummary> { it.balance.isPositive }
                .thenBy(nullsFirst()) { it.lastPaymentDate.takeIf { _ -> it.balance.isPositive } }
                .thenByDescending { it.balance.minor }
            LAST_PAYMENT -> compareBy(nullsLast(reverseOrder())) { it.lastPaymentDate }
            LAST_ORDER -> compareBy(nullsLast(reverseOrder())) { it.lastOrderDate }
            MOST_ORDERS -> compareByDescending { it.orderCount }
            NEWEST -> compareByDescending { it.createdAt }
            MOST_ITEMS -> compareByDescending { it.itemCount }
        }
        return clients.sortedWith(first.thenComparing({ it.client }, byName))
    }
}

object ClientNames {
    /** Legal forms written before or after a firm's name: they don't say where it belongs in a list. */
    private val LEGAL_FORMS = setOf(
        "սպը", "ափը", "փբը", "բբը", "աձ", "հկ", "պոակ", "ոակ",
        "ооо", "оао", "зао", "пао", "ао", "ип",
        "llc", "ltd", "inc", "cjsc", "ojsc", "jsc", "ie", "pe",
    )

    /** "«ԱԲԳ Սերվիս» ՍՊԸ" → "աբգ սերվիս": what the name is sorted by. */
    fun sortKey(name: String): String {
        val words = ClientSearch.normalizeText(name).split(' ').filter { it.isNotEmpty() }
        val trimmed = words.dropWhile { it in LEGAL_FORMS }.dropLastWhile { it in LEGAL_FORMS }
        return trimmed.ifEmpty { words }.joinToString(" ")
    }

    /** Names in Armenian alphabet order (Latin and Cyrillic in theirs), ignoring case and accents. */
    fun comparator(): Comparator<Client> {
        val collator = Collator.getInstance(Locale.forLanguageTag("hy")).apply { strength = Collator.SECONDARY }
        return Comparator { a, b ->
            collator.compare(sortKey(a.name), sortKey(b.name)).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }
}

/** How the Payments list is ordered; every order falls back to the name. */
enum class PaymentSort {
    /** Who owes the most first. */
    DEBT,

    /** Debtors who paid longest ago (or never) first. */
    LONGEST_UNPAID,

    /** Most recent payment first; never paid last. */
    LAST_PAYMENT,

    /** Lowest share of everything billed paid first (nothing paid = 0 %). */
    LEAST_PAID_SHARE,

    /** Billed the most in total first: the biggest clients. */
    MOST_CHARGED,

    /** Paid the most in total first. */
    MOST_PAID,

    /** A to Я, as on the Clients tab. */
    NAME,
    ;

    fun sort(clients: List<ClientSummary>): List<ClientSummary> = when (this) {
        DEBT -> ClientSort.DEBT.sort(clients)
        LONGEST_UNPAID -> ClientSort.LONGEST_UNPAID.sort(clients)
        LAST_PAYMENT -> ClientSort.LAST_PAYMENT.sort(clients)
        NAME -> ClientSort.NAME.sort(clients)
        LEAST_PAID_SHARE -> clients.sortedWith(
            compareBy<ClientSummary> { !it.charged.isPositive }.thenBy { paidShare(it) }.thenComparing({ it.client }, ClientNames.comparator()),
        )
        MOST_CHARGED -> clients.sortedWith(compareByDescending<ClientSummary> { it.charged.minor }.thenComparing({ it.client }, ClientNames.comparator()))
        MOST_PAID -> clients.sortedWith(compareByDescending<ClientSummary> { it.paid.minor }.thenComparing({ it.client }, ClientNames.comparator()))
    }

    companion object {
        /** Paid ÷ billed, 0…1; clients billed nothing count as fully paid. */
        fun paidShare(summary: ClientSummary): Double =
            if (!summary.charged.isPositive) 1.0 else (summary.paid.minor.toDouble() / summary.charged.minor).coerceIn(0.0, 1.0)
    }
}
