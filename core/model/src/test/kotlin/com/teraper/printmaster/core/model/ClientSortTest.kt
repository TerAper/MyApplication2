package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ClientSortTest {

    private var nextId = 1L

    private fun client(
        name: String,
        debt: Long = 0,
        paid: LocalDate? = null,
        orders: Int = 0,
        lastOrder: LocalDate? = null,
        created: Long = 0,
        items: Int = 0,
    ) = ClientSummary(
        client = Client(nextId++, name, ClientType.FIRM, null, "", emptyList(), emptyList()),
        itemCount = items,
        charged = Money.ofDram(debt.coerceAtLeast(0)),
        paid = Money.ofDram((-debt).coerceAtLeast(0)),
        lastPaymentDate = paid,
        orderCount = orders,
        lastOrderDate = lastOrder,
        createdAt = created,
    )

    private fun List<ClientSummary>.names() = map { it.client.name }

    @Test
    fun namesIgnoreQuotesAndLegalFormsAndFollowTheAlphabet() {
        assertEquals("աբգ սերվիս", ClientNames.sortKey("«ԱԲԳ Սերվիս» ՍՊԸ"))
        assertEquals("ромашка", ClientNames.sortKey("ООО \"Ромашка\""))
        assertEquals("alpha", ClientNames.sortKey("Alpha LLC"))
        // A name that is only a legal form keeps it, rather than sorting as empty.
        assertEquals("սպը", ClientNames.sortKey("ՍՊԸ"))

        val list = listOf(client("Վարդան"), client("«Գամմա» ՍՊԸ"), client("աննա"), client("«Բետա» ՓԲԸ"))
        assertEquals(listOf("աննա", "«Բետա» ՓԲԸ", "«Գամմա» ՍՊԸ", "Վարդան"), ClientSort.NAME.sort(list).names())
    }

    @Test
    fun debtAndLongestUnpaidPutWhoToCallFirst() {
        val day = LocalDate.of(2026, 10, 1)
        val list = listOf(
            client("Settled", debt = 0, paid = day),
            client("Big recent", debt = 90_000, paid = day),
            client("Small never", debt = 1_000, paid = null),
            client("Old", debt = 5_000, paid = day.minusMonths(6)),
            client("Overpaid", debt = -2_000, paid = day.minusYears(1)),
        )
        assertEquals(listOf("Big recent", "Old", "Small never", "Settled", "Overpaid"), ClientSort.DEBT.sort(list).names())
        // Debtors who never paid, then the oldest payment; those who owe nothing after.
        assertEquals(listOf("Small never", "Old", "Big recent", "Settled", "Overpaid"), ClientSort.LONGEST_UNPAID.sort(list).names())
        assertEquals(listOf("Big recent", "Settled", "Old", "Overpaid", "Small never"), ClientSort.LAST_PAYMENT.sort(list).names())
    }

    @Test
    fun visitsOrdersNewAndItemsWithNameAsTieBreak() {
        val day = LocalDate.of(2026, 10, 1)
        val list = listOf(
            client("B", orders = 3, lastOrder = day, created = 10, items = 1),
            client("A", orders = 3, lastOrder = day.minusDays(5), created = 30, items = 1),
            client("C", orders = 0, lastOrder = null, created = 20, items = 4),
        )
        assertEquals(listOf("B", "A", "C"), ClientSort.LAST_ORDER.sort(list).names())
        assertEquals(listOf("A", "B", "C"), ClientSort.MOST_ORDERS.sort(list).names())
        assertEquals(listOf("A", "C", "B"), ClientSort.NEWEST.sort(list).names())
        assertEquals(listOf("C", "A", "B"), ClientSort.MOST_ITEMS.sort(list).names())
    }

    @Test
    fun paymentSortsByShareBilledAndPaid() {
        fun billed(name: String, charged: Long, paid: Long) = ClientSummary(
            client = Client(nextId++, name, ClientType.FIRM, null, "", emptyList(), emptyList()),
            charged = Money.ofDram(charged),
            paid = Money.ofDram(paid),
        )
        val list = listOf(billed("Half", 1_000, 500), billed("None", 200, 0), billed("All", 9_000, 9_000), billed("Nothing billed", 0, 300))
        // Nothing paid of something billed first; nothing billed at all last.
        assertEquals(listOf("None", "Half", "All", "Nothing billed"), PaymentSort.LEAST_PAID_SHARE.sort(list).names())
        assertEquals(listOf("All", "Half", "None", "Nothing billed"), PaymentSort.MOST_CHARGED.sort(list).names())
        assertEquals(listOf("All", "Half", "Nothing billed", "None"), PaymentSort.MOST_PAID.sort(list).names())
        assertEquals(listOf("Half", "None", "All", "Nothing billed"), PaymentSort.DEBT.sort(list).names())
    }
}
