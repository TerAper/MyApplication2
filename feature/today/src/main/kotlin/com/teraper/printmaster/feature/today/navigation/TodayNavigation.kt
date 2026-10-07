package com.teraper.printmaster.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.today.TodayActions
import com.teraper.printmaster.feature.today.TodayRoute
import kotlinx.serialization.Serializable

@Serializable
data object TodayDestination

fun NavController.navigateToToday(navOptions: NavOptions? = null) = navigate(TodayDestination, navOptions)

/** [actions] lead to other features (orders, payments); the app wires them. */
fun NavGraphBuilder.todayScreen(actions: TodayActions) {
    composable<TodayDestination> { TodayRoute(actions) }
}
