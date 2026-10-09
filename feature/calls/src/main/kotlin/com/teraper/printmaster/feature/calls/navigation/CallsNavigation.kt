package com.teraper.printmaster.feature.calls.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.calls.CallsRoute
import kotlinx.serialization.Serializable

@Serializable
data object CallsDestination

fun NavController.navigateToCalls() = navigate(CallsDestination)

/** The call recordings screen; the app puts it inside the More tab. */
fun NavGraphBuilder.callsScreens(navController: NavController, onOpenClient: (Long) -> Unit) {
    composable<CallsDestination> {
        CallsRoute(onBack = navController::popBackStack, onOpenClient = onOpenClient)
    }
}
