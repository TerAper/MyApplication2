package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.ExpensesRepository
import com.teraper.printmaster.core.data.repository.SaveExpenseResult
import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseDraft
import com.teraper.printmaster.core.model.ExpenseTotals
import com.teraper.printmaster.core.model.sum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.YearMonth

class FakeExpensesRepository(initial: List<Expense> = emptyList()) : ExpensesRepository {

    val expenses = MutableStateFlow(initial)

    private fun inMonth(month: YearMonth) = expenses.map { list -> list.filter { YearMonth.from(it.date) == month } }

    override fun observeExpenses(month: YearMonth): Flow<List<Expense>> = inMonth(month).map { l -> l.sortedByDescending { it.date } }

    override fun observeTotals(month: YearMonth): Flow<ExpenseTotals> =
        inMonth(month).map { l -> ExpenseTotals(l.groupBy { it.category }.mapValues { (_, v) -> v.map { it.amount }.sum() }) }

    override fun observeExpense(id: Long): Flow<Expense?> = expenses.map { l -> l.firstOrNull { it.id == id } }

    override suspend fun save(draft: ExpenseDraft): SaveExpenseResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveExpenseResult.Invalid(errors)
        val id = if (draft.isNew) (expenses.value.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
        val expense = Expense(id, draft.date, draft.amount, draft.category, draft.note)
        expenses.update { l -> l.filterNot { it.id == id } + expense }
        return SaveExpenseResult.Saved(id)
    }

    override suspend fun delete(id: Long): Boolean {
        val had = expenses.value.any { it.id == id }
        expenses.update { l -> l.filterNot { it.id == id } }
        return had
    }
}
