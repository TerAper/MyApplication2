package com.teraper.printmaster.feature.reports.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.reports.export.DebtExportRoute
import com.teraper.printmaster.feature.reports.overview.ReportsRoute
import kotlinx.serialization.Serializable

@Serializable
data object ReportsDestination

@Serializable
data object DebtExportDestination

fun NavController.navigateToReports() = navigate(ReportsDestination)

fun NavController.navigateToDebtExport() = navigate(DebtExportDestination)

/** Reports and the debt export; the app puts them inside the More tab. */
fun NavGraphBuilder.reportsScreens(navController: NavController, onOpenClient: (Long) -> Unit, onOpenExpenses: () -> Unit = {}) {
    composable<ReportsDestination> {
        ReportsRoute(
            onBack = navController::popBackStack,
            onOpenClient = onOpenClient,
            onExportDebts = navController::navigateToDebtExport,
            onOpenExpenses = onOpenExpenses,
        )
    }
    composable<DebtExportDestination> {
        DebtExportRoute(onBack = navController::popBackStack)
    }
}
