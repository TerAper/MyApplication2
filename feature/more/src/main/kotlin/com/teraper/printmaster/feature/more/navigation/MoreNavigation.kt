package com.teraper.printmaster.feature.more.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.more.MoreRoute
import kotlinx.serialization.Serializable

@Serializable
data object MoreDestination

fun NavController.navigateToMore(navOptions: NavOptions? = null) = navigate(MoreDestination, navOptions)

fun NavGraphBuilder.moreScreen() {
    composable<MoreDestination> { MoreRoute() }
}
