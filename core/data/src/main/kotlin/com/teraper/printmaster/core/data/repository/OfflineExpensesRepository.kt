package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.ExpenseDao
import com.teraper.printmaster.core.database.entity.ExpenseEntity
import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseDraft
import com.teraper.printmaster.core.model.ExpenseTotals
import com.teraper.printmaster.core.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

internal class OfflineExpensesRepository @Inject constructor(
    private val dao: ExpenseDao,
    private val companies: CompaniesRepository,
    private val clock: Clock,
) : ExpensesRepository {

    override fun observeExpenses(month: YearMonth): Flow<List<Expense>> = companies.forActiveCompany(emptyList()) { companyId ->
        dao.observeExpenses(companyId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { rows -> rows.map { it.toModel() } }
    }

    override fun observeTotals(month: YearMonth): Flow<ExpenseTotals> = companies.forActiveCompany(ExpenseTotals()) { companyId ->
        dao.observeTotals(companyId, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { rows ->
            ExpenseTotals(rows.associate { it.category to Money(it.totalMinor) })
        }
    }

    override fun observeExpense(id: Long): Flow<Expense?> = dao.observeExpense(id).map { it?.toModel() }

    override suspend fun save(draft: ExpenseDraft): SaveExpenseResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveExpenseResult.Invalid(errors)
        return if (draft.isNew) {
            val companyId = companies.observeActiveCompany().first()?.id ?: return SaveExpenseResult.Invalid(emptySet())
            SaveExpenseResult.Saved(
                dao.insert(
                    ExpenseEntity(
                        companyId = companyId,
                        dateEpochDay = draft.date.toEpochDay(),
                        amountMinor = draft.amount.minor,
                        category = draft.category,
                        note = draft.note.trim(),
                        createdAt = clock.millis(),
                    ),
                ),
            )
        } else {
            val existing = dao.getExpense(draft.id) ?: return SaveExpenseResult.Invalid(emptySet())
            dao.update(existing.copy(dateEpochDay = draft.date.toEpochDay(), amountMinor = draft.amount.minor, category = draft.category, note = draft.note.trim()))
            SaveExpenseResult.Saved(existing.id)
        }
    }

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    private fun ExpenseEntity.toModel() = Expense(id, LocalDate.ofEpochDay(dateEpochDay), Money(amountMinor), category, note)
}
