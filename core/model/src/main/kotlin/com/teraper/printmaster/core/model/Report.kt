package com.teraper.printmaster.core.model

import java.time.YearMonth

/** What clients were charged in a period, by where the charge came from. */
data class BilledTotals(
    val repairs: Money = Money.ZERO,
    val invoices: Money = Money.ZERO,
    val manual: Money = Money.ZERO,
) {
    val total: Money get() = repairs + invoices + manual
}

/** Repair work billed in a period: what it was sold for and what the parts cost. */
data class WorkTotals(
    val orders: Int = 0,
    val revenue: Money = Money.ZERO,
    val cost: Money = Money.ZERO,
) {
    val profit: Money get() = revenue - cost

    /** Profit as a share of revenue, 0–100; null without revenue. */
    val marginPercent: Int? get() = if (revenue.isPositive) (profit.minor * 100 / revenue.minor).toInt() else null
}

/** One month of the active company's money. */
data class MonthReport(
    val month: YearMonth,
    val income: IncomeTotals = IncomeTotals(),
    val billed: BilledTotals = BilledTotals(),
    val work: WorkTotals = WorkTotals(),
    /** Spending typed by hand. */
    val expenses: ExpenseTotals = ExpenseTotals(),
) {
    /** Money actually left: what came in minus what went out. */
    val cashLeft: Money get() = income.total - expenses.total

    /** What was earned: billed, minus parts cost from the price list, minus expenses. */
    val earned: Money get() = billed.total - work.cost - expenses.total
}

/** Money received in one month, for the bar chart. */
data class MonthIncome(val month: YearMonth, val income: IncomeTotals)
