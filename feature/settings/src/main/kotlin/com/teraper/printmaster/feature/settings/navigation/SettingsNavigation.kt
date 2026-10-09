package com.teraper.printmaster.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.settings.backup.BackupRoute
import kotlinx.serialization.Serializable

@Serializable
data object BackupDestination

fun NavController.navigateToBackup() = navigate(BackupDestination)

/** Backup and settings screens; the app puts them inside the More tab. */
fun NavGraphBuilder.settingsScreens(navController: NavController) {
    composable<BackupDestination> {
        BackupRoute(onBack = navController::popBackStack)
    }
}
