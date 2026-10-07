package com.teraper.printmaster.feature.clients.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.clients.ClientsRoute
import kotlinx.serialization.Serializable

@Serializable
data object ClientsDestination

fun NavController.navigateToClients(navOptions: NavOptions? = null) = navigate(ClientsDestination, navOptions)

fun NavGraphBuilder.clientsScreen() {
    composable<ClientsDestination> { ClientsRoute() }
}
