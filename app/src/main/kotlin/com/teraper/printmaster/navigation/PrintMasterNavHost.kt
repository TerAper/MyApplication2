package com.teraper.printmaster.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.feature.catalog.navigation.ModelEditDestination
import com.teraper.printmaster.feature.catalog.navigation.catalogScreens
import com.teraper.printmaster.feature.catalog.navigation.navigateToCatalog
import com.teraper.printmaster.feature.clients.navigation.ClientEditDestination
import com.teraper.printmaster.feature.clients.navigation.PrinterEditDestination
import com.teraper.printmaster.feature.clients.navigation.clientsGraph
import com.teraper.printmaster.feature.clients.navigation.navigateToClientDetail
import com.teraper.printmaster.feature.more.navigation.moreGraph
import com.teraper.printmaster.feature.orders.navigation.ordersScreen
import com.teraper.printmaster.feature.payments.navigation.MoneyEntryDestination
import com.teraper.printmaster.feature.payments.navigation.navigateToMoneyEntry
import com.teraper.printmaster.feature.payments.navigation.paymentsGraph
import com.teraper.printmaster.feature.today.navigation.TodayDestination
import com.teraper.printmaster.feature.today.navigation.todayScreen

/**
 * The only place that knows about every feature. Links between features
 * (e.g. client card → cash payment) are wired here, so features stay independent.
 */
@Composable
fun PrintMasterNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TodayDestination,
        modifier = modifier,
    ) {
        todayScreen()
        clientsGraph(
            navController = navController,
            onRecordPayment = { navController.navigateToMoneyEntry(it, MoneyEntryKind.CASH_PAYMENT) },
            onAddCharge = { navController.navigateToMoneyEntry(it, MoneyEntryKind.MANUAL_CHARGE) },
        )
        ordersScreen()
        paymentsGraph(
            navController = navController,
            onOpenClient = navController::openClientFromOtherTab,
        )
        moreGraph(onOpenCatalog = navController::navigateToCatalog) {
            catalogScreens(navController, onOpenClient = navController::openClientFromOtherTab)
        }
    }
}

/**
 * A client card opened from another tab (Payments, catalog) moves to the Clients tab,
 * so each tab keeps its own back stack and Back from the card returns to the client list.
 */
private fun NavHostController.openClientFromOtherTab(clientId: Long) {
    navigateToTab(TopLevelDestination.CLIENTS, restore = false)
    navigateToClientDetail(clientId)
}

/** Forms that use the whole screen; the bottom bar is hidden on them. */
private val fullScreenRoutes = listOf(
    ClientEditDestination::class,
    PrinterEditDestination::class,
    MoneyEntryDestination::class,
    ModelEditDestination::class,
)

fun NavDestination?.isFullScreen(): Boolean = this != null && fullScreenRoutes.any { hasRoute(it) }
