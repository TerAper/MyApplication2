package com.teraper.printmaster.feature.team.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.team.space.TeamRoute
import kotlinx.serialization.Serializable

@Serializable
data object TeamDestination

fun NavController.navigateToTeam() = navigate(TeamDestination)

/** The company's sharing screen; the app puts it inside the More tab. */
fun NavGraphBuilder.teamScreens(navController: NavController) {
    composable<TeamDestination> { TeamRoute(onBack = navController::popBackStack) }
}
