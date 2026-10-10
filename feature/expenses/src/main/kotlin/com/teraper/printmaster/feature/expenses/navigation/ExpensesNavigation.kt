package com.teraper.printmaster.feature.expenses.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.expenses.edit.ExpenseEditRoute
import com.teraper.printmaster.feature.expenses.list.ExpensesRoute
import kotlinx.serialization.Serializable

@Serializable
data object ExpensesDestination

/** [expenseId] 0 = a new expense. */
@Serializable
data class ExpenseEditDestination(val expenseId: Long = 0)

internal const val EXPENSE_ID_ARG = "expenseId"

fun NavController.navigateToExpenses() = navigate(ExpensesDestination)

/** Expenses list and form; the app puts them inside the More tab. */
fun NavGraphBuilder.expensesScreens(navController: NavController) {
    composable<ExpensesDestination> {
        ExpensesRoute(
            onBack = navController::popBackStack,
            onAdd = { navController.navigate(ExpenseEditDestination()) },
            onOpen = { navController.navigate(ExpenseEditDestination(it)) },
        )
    }
    composable<ExpenseEditDestination> {
        ExpenseEditRoute(onClose = navController::popBackStack)
    }
}
