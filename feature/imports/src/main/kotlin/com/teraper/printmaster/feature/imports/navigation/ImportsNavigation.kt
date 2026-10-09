package com.teraper.printmaster.feature.imports.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.imports.file.ImportRoute
import com.teraper.printmaster.feature.imports.history.HistoryRoute
import com.teraper.printmaster.feature.imports.review.ReviewRoute
import kotlinx.serialization.Serializable

@Serializable
data object ImportDestination

@Serializable
data object ReviewPaymentsDestination

@Serializable
data object ImportHistoryDestination

fun NavController.navigateToImport() = navigate(ImportDestination)

fun NavController.navigateToReviewPayments() = navigate(ReviewPaymentsDestination)

fun NavController.navigateToImportHistory() = navigate(ImportHistoryDestination)

/** Import, review and history; the app puts them inside the Payments tab. */
fun NavGraphBuilder.importScreens(navController: NavController) {
    composable<ImportDestination> {
        ImportRoute(
            onBack = navController::popBackStack,
            onReviewPayments = navController::navigateToReviewPayments,
            onOpenHistory = navController::navigateToImportHistory,
        )
    }
    composable<ReviewPaymentsDestination> {
        ReviewRoute(onBack = navController::popBackStack)
    }
    composable<ImportHistoryDestination> {
        HistoryRoute(onBack = navController::popBackStack, onImport = navController::navigateToImport)
    }
}
