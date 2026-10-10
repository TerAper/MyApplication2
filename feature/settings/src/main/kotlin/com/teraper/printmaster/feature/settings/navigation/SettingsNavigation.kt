package com.teraper.printmaster.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.settings.backup.BackupRoute
import com.teraper.printmaster.feature.settings.columns.ExcelColumnsRoute
import com.teraper.printmaster.feature.settings.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object BackupDestination

@Serializable
data object SettingsDestination

@Serializable
data object ExcelColumnsDestination

fun NavController.navigateToBackup() = navigate(BackupDestination)

fun NavController.navigateToSettings() = navigate(SettingsDestination)

fun NavController.navigateToExcelColumns() = navigate(ExcelColumnsDestination)

/** Backup and settings screens; the app puts them inside the More tab. */
fun NavGraphBuilder.settingsScreens(navController: NavController) {
    composable<BackupDestination> {
        BackupRoute(onBack = navController::popBackStack)
    }
    composable<SettingsDestination> {
        SettingsRoute(onBack = navController::popBackStack, onOpenColumns = navController::navigateToExcelColumns)
    }
    composable<ExcelColumnsDestination> {
        ExcelColumnsRoute(onBack = navController::popBackStack)
    }
}
