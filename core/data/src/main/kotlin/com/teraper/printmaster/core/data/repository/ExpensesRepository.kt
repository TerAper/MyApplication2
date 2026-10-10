package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseDraft
import com.teraper.printmaster.core.model.ExpenseError
import com.teraper.printmaster.core.model.ExpenseTotals
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Spending typed by hand, for the active company. */
interface ExpensesRepository {

    /** Newest first. */
    fun observeExpenses(month: YearMonth): Flow<List<Expense>>

    fun observeTotals(month: YearMonth): Flow<ExpenseTotals>

    fun observeExpense(id: Long): Flow<Expense?>

    suspend fun save(draft: ExpenseDraft): SaveExpenseResult

    suspend fun delete(id: Long): Boolean
}

sealed interface SaveExpenseResult {
    data class Saved(val id: Long) : SaveExpenseResult
    data class Invalid(val errors: Set<ExpenseError>) : SaveExpenseResult
}
