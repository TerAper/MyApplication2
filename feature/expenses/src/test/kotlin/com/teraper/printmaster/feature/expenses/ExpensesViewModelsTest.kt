package com.teraper.printmaster.feature.expenses

import androidx.lifecycle.SavedStateHandle
import com.teraper.printmaster.core.model.Expense
import com.teraper.printmaster.core.model.ExpenseCategory
import com.teraper.printmaster.core.model.ExpenseError
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.testing.FakeExpensesRepository
import com.teraper.printmaster.core.testing.FakePhotoRepository
import com.teraper.printmaster.core.testing.MainDispatcherRule
import com.teraper.printmaster.feature.expenses.edit.ExpenseEditEvent
import com.teraper.printmaster.feature.expenses.edit.ExpenseEditViewModel
import com.teraper.printmaster.feature.expenses.list.ExpensesViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 8)

    private fun expense(id: Long, date: LocalDate, dram: Long, category: ExpenseCategory = ExpenseCategory.PARTS) =
        Expense(id, date, Money.ofDram(dram), category)

    private fun TestScope.collect(flow: StateFlow<*>) = backgroundScope.launch(UnconfinedTestDispatcher()) { flow.collect {} }

    @Test
    fun listGroupsByDayNewestFirstAndSwitchesMonths() = runTest {
        val repo = FakeExpensesRepository(
            listOf(
                expense(1, today.minusDays(3), 2_000, ExpenseCategory.TONER),
                expense(2, today, 500, ExpenseCategory.TRANSPORT),
                expense(3, today, 1_000),
                expense(4, LocalDate.of(2026, 9, 20), 7_000),
            ),
        )
        val vm = ExpensesViewModel(repo, clock)
        collect(vm.uiState)

        val state = vm.uiState.value
        assertEquals(listOf(today, today.minusDays(3)), state.days.map { it.first })
        assertEquals(Money.ofDram(3_500), state.totals.total)
        assertFalse(state.canGoNext)

        vm.onNextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)

        vm.onPreviousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.month)
        assertEquals(Money.ofDram(7_000), vm.uiState.value.totals.total)
        assertTrue(vm.uiState.value.canGoNext)
    }

    @Test
    fun newExpenseNeedsAnAmountThenSavesForToday() = runTest {
        val repo = FakeExpensesRepository()
        val vm = ExpenseEditViewModel(SavedStateHandle(), repo, FakePhotoRepository(), clock)
        collect(vm.uiState)

        vm.onSave()
        assertEquals(setOf(ExpenseError.AMOUNT_REQUIRED), vm.uiState.value.form.errors)

        vm.onAmountChange("0015a00")
        vm.onCategoryChange(ExpenseCategory.TONER)
        vm.onNoteChange("Toner HP 85A")
        assertTrue(vm.uiState.value.form.errors.isEmpty())
        vm.onSave()
        assertEquals(ExpenseEditEvent.Close, vm.events.first())

        val saved = repo.expenses.value.single()
        assertEquals(Money.ofDram(1_500), saved.amount)
        assertEquals(today, saved.date)
        assertEquals(ExpenseCategory.TONER, saved.category)
    }

    @Test
    fun existingExpenseLoadsEditsAndDeletes() = runTest {
        val repo = FakeExpensesRepository(listOf(expense(7, today.minusDays(1), 3_000)))
        val vm = ExpenseEditViewModel(SavedStateHandle(mapOf("expenseId" to 7L)), repo, FakePhotoRepository(), clock)
        collect(vm.uiState)

        assertEquals("3000", vm.uiState.value.form.draft.amountDigits)
        vm.onDateChange(today.minusDays(2))
        vm.onSave()
        assertEquals(ExpenseEditEvent.Close, vm.events.first())
        assertEquals(today.minusDays(2), repo.expenses.value.single().date)

        vm.onDeleteClick()
        assertTrue(vm.uiState.value.form.confirmDelete)
        vm.onConfirmDelete()
        assertEquals(ExpenseEditEvent.Close, vm.events.first())
        assertTrue(repo.expenses.value.isEmpty())
    }
}
