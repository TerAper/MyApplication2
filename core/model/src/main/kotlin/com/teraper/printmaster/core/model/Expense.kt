package com.teraper.printmaster.core.model

import java.time.LocalDate

/** What the money was spent on. */
enum class ExpenseCategory { PARTS, TONER, TRANSPORT, RENT, SALARY, TAXES, OTHER }

/** Money the company spent, typed by hand (receipts, fuel, rent…). */
data class Expense(
    val id: Long,
    val date: LocalDate,
    val amount: Money,
    val category: ExpenseCategory,
    val note: String = "",
)

/** The expense form. [id] 0 = new. */
data class ExpenseDraft(
    val id: Long = 0,
    val amountDigits: String = "",
    val category: ExpenseCategory = ExpenseCategory.PARTS,
    val date: LocalDate,
    val note: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val amount: Money get() = Money.ofDram(amountDigits.toLongOrNull() ?: 0)

    fun validate(): Set<ExpenseError> = buildSet {
        if (!amount.isPositive) add(ExpenseError.AMOUNT_REQUIRED)
    }

    /** Digits only, no leading zeros, at most 10 digits (same as prices). */
    fun withAmount(text: String) = copy(amountDigits = PriceItemDraft.amountDigits(text))

    companion object {
        fun from(expense: Expense) = ExpenseDraft(expense.id, expense.amount.dram.toString(), expense.category, expense.date, expense.note)
    }
}

enum class ExpenseError { AMOUNT_REQUIRED }

/** A month of expenses, by category. */
data class ExpenseTotals(val byCategory: Map<ExpenseCategory, Money> = emptyMap()) {
    val total: Money get() = byCategory.values.sum()
}
