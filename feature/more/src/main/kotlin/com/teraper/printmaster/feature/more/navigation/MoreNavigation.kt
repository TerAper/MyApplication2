package com.teraper.printmaster.feature.more.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.teraper.printmaster.feature.more.MoreActions
import com.teraper.printmaster.feature.more.MoreRoute
import kotlinx.serialization.Serializable

/** The More tab: the menu plus whatever screens the app nests under it (catalog, …). */
@Serializable
data object MoreGraph

@Serializable
data object MoreDestination

fun NavController.navigateToMore(navOptions: NavOptions? = null) = navigate(MoreGraph, navOptions)

/**
 * [actions] open other features (catalog, companies, …); [nestedGraphs] adds their screens
 * inside this tab so the More tab stays highlighted on them.
 */
fun NavGraphBuilder.moreGraph(
    actions: MoreActions,
    nestedGraphs: NavGraphBuilder.() -> Unit,
) {
    navigation<MoreGraph>(startDestination = MoreDestination) {
        composable<MoreDestination> { MoreRoute(actions = actions) }
        nestedGraphs()
    }
}
