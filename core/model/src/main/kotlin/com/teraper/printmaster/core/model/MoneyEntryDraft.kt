package com.teraper.printmaster.core.model

import java.time.LocalDate

enum class MoneyEntryKind {
    /** Cash the client handed over. */
    CASH_PAYMENT,
    /** A debt typed by hand, e.g. an old debt from before the app. */
    MANUAL_CHARGE,
}

/**
 * What the cash payment / new debt screen holds before saving.
 * [amountDigits] is what was typed on the keypad, in whole dram.
 */
data class MoneyEntryDraft(
    val kind: MoneyEntryKind,
    val clientId: Long? = null,
    /** Company the money goes under; starts as the company being viewed. */
    val companyId: Long? = null,
    val amountDigits: String = "",
    val date: LocalDate,
    val note: String = "",
) {
    val amount: Money get() = Money.ofDram(amountDigits.toLongOrNull() ?: 0L)

    fun validate(): Set<MoneyEntryError> = buildSet {
        if (clientId == null) add(MoneyEntryError.CLIENT_REQUIRED)
        if (companyId == null) add(MoneyEntryError.COMPANY_REQUIRED)
        if (!amount.isPositive) add(MoneyEntryError.AMOUNT_REQUIRED)
    }

    /** Keypad input: digits, "000" or delete. Leading zeros dropped, at most [MAX_DIGITS] digits. */
    fun typed(key: String): MoneyEntryDraft {
        val next = when (key) {
            KEY_DELETE -> amountDigits.dropLast(1)
            else -> (amountDigits + key).trimStart('0')
        }
        return if (next.length > MAX_DIGITS) this else copy(amountDigits = next)
    }

    fun withAmount(amount: Money): MoneyEntryDraft = copy(amountDigits = amount.dram.coerceAtLeast(0).toString().takeIf { it != "0" }.orEmpty())

    companion object {
        const val KEY_DELETE = "⌫"
        /** Up to 9 999 999 999 ֏, well above any real payment. */
        const val MAX_DIGITS = 10
    }
}

enum class MoneyEntryError { CLIENT_REQUIRED, COMPANY_REQUIRED, AMOUNT_REQUIRED }
