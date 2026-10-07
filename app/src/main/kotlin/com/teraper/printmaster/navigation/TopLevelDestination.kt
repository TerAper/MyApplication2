package com.teraper.printmaster.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.teraper.printmaster.R
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.feature.clients.navigation.ClientsGraph
import com.teraper.printmaster.feature.more.navigation.MoreGraph
import com.teraper.printmaster.feature.orders.navigation.OrdersGraph
import com.teraper.printmaster.feature.payments.navigation.PaymentsGraph
import com.teraper.printmaster.feature.today.navigation.TodayDestination
import kotlin.reflect.KClass

/** The five bottom-bar tabs. Reordering tabs = reordering this enum. */
enum class TopLevelDestination(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val route: Any,
    val routeClass: KClass<*>,
) {
    TODAY(PmIcons.Today, R.string.tab_today, TodayDestination, TodayDestination::class),
    CLIENTS(PmIcons.Clients, R.string.tab_clients, ClientsGraph, ClientsGraph::class),
    ORDERS(PmIcons.Orders, R.string.tab_orders, OrdersGraph, OrdersGraph::class),
    PAYMENTS(PmIcons.Payments, R.string.tab_payments, PaymentsGraph, PaymentsGraph::class),
    MORE(PmIcons.More, R.string.tab_more, MoreGraph, MoreGraph::class),
}

/** Switch tabs keeping each tab's own back stack and scroll position. */
internal fun NavHostController.navigateToTab(tab: TopLevelDestination, restore: Boolean = true) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

/**
 * The tab a back stack belongs to: the nearest screen that sits inside a tab.
 * Shared screens (like an order) opened from a tab keep that tab highlighted.
 */
internal fun List<NavBackStackEntry>.currentTab(): TopLevelDestination? =
    asReversed().firstNotNullOfOrNull { entry ->
        TopLevelDestination.entries.firstOrNull { tab -> entry.destination.hierarchy.any { it.hasRoute(tab.routeClass) } }
    }
