package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.ExpenseDao
import com.teraper.printmaster.core.database.dao.PaymentRow
import com.teraper.printmaster.core.database.dao.ReportDao
import com.teraper.printmaster.core.model.BilledTotals
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ExpenseTotals
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.core.model.MonthReport
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.WorkTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

internal class OfflineReportsRepository @Inject constructor(
    private val dao: ReportDao,
    private val expenseDao: ExpenseDao,
    private val companies: CompaniesRepository,
) : ReportsRepository {

    override fun observeMonth(month: YearMonth): Flow<MonthReport> = companies.forActiveCompany(MonthReport(month)) { companyId ->
        val from = month.atDay(1).toEpochDay()
        val to = month.atEndOfMonth().toEpochDay()
        combine(
            dao.observePayments(companyId, from, to),
            dao.observeBilledBySource(companyId, from, to),
            dao.observeWork(companyId, from, to),
            expenseDao.observeTotals(companyId, from, to),
        ) { payments, billed, work, expenses ->
            val bySource = billed.associate { it.source to Money(it.totalMinor) }
            MonthReport(
                month = month,
                income = payments.incomeTotals(),
                billed = BilledTotals(
                    repairs = bySource[ChargeSource.REPAIR] ?: Money.ZERO,
                    invoices = bySource[ChargeSource.INVOICE_IMPORT] ?: Money.ZERO,
                    manual = bySource[ChargeSource.MANUAL] ?: Money.ZERO,
                ),
                work = WorkTotals(work.orders, Money(work.revenueMinor), Money(work.costMinor)),
                expenses = ExpenseTotals(expenses.associate { it.category to Money(it.totalMinor) }),
            )
        }
    }

    override fun observeIncomeHistory(last: YearMonth, count: Int): Flow<List<MonthIncome>> {
        val months = (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }
        val empty = months.map { MonthIncome(it, IncomeTotals()) }
        return companies.forActiveCompany(empty) { companyId ->
            dao.observePayments(companyId, months.first().atDay(1).toEpochDay(), last.atEndOfMonth().toEpochDay()).map { rows ->
                val byMonth = rows.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) }
                months.map { MonthIncome(it, byMonth[it].orEmpty().incomeTotals()) }
            }
        }
    }

    private fun List<PaymentRow>.incomeTotals() = IncomeTotals(
        cash = Money(filter { it.method == PaymentMethod.CASH }.sumOf { it.amountMinor }),
        bank = Money(filter { it.method == PaymentMethod.BANK }.sumOf { it.amountMinor }),
    )
}
