package com.teraper.printmaster.core.model

import java.time.LocalDate

/** One line of a client's money history: something charged, or something paid. */
sealed interface LedgerEntry {
    val id: Long
    val date: LocalDate
    val amount: Money
    val note: String
    /** For ordering entries made on the same day. */
    val createdAt: Long

    /**
     * Only entries typed on the phone can be deleted here. Imported ones are removed by
     * undoing their import, repair charges by changing the repair.
     */
    val canDelete: Boolean

    data class Charge(
        override val id: Long,
        override val date: LocalDate,
        override val amount: Money,
        override val note: String,
        override val createdAt: Long,
        val source: ChargeSource,
        val documentNumber: String?,
    ) : LedgerEntry {
        override val canDelete get() = source == ChargeSource.MANUAL
    }

    data class Payment(
        override val id: Long,
        override val date: LocalDate,
        override val amount: Money,
        override val note: String,
        override val createdAt: Long,
        val method: PaymentMethod,
        val reference: String?,
    ) : LedgerEntry {
        override val canDelete get() = method == PaymentMethod.CASH
    }
}

/** Newest first; same day → newest typed first. */
fun List<LedgerEntry>.sortedNewestFirst(): List<LedgerEntry> =
    sortedWith(compareByDescending<LedgerEntry> { it.date }.thenByDescending { it.createdAt })

/** Money received in a period, split by how it was paid. */
data class IncomeTotals(val cash: Money = Money.ZERO, val bank: Money = Money.ZERO) {
    val total: Money get() = cash + bank
}
