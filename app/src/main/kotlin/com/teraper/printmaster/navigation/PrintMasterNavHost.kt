package com.teraper.printmaster.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.feature.account.navigation.CompanyEditDestination
import com.teraper.printmaster.feature.account.navigation.accountScreens
import com.teraper.printmaster.feature.account.navigation.navigateToCompanies
import com.teraper.printmaster.feature.account.navigation.navigateToCompany
import com.teraper.printmaster.feature.account.navigation.navigateToMasters
import com.teraper.printmaster.feature.calls.navigation.callsScreens
import com.teraper.printmaster.feature.calls.navigation.navigateToCalls
import com.teraper.printmaster.feature.catalog.navigation.ModelEditDestination
import com.teraper.printmaster.feature.catalog.navigation.catalogScreens
import com.teraper.printmaster.feature.catalog.navigation.navigateToCatalog
import com.teraper.printmaster.feature.clients.navigation.ClientEditDestination
import com.teraper.printmaster.feature.clients.navigation.PrinterEditDestination
import com.teraper.printmaster.feature.clients.navigation.clientsGraph
import com.teraper.printmaster.feature.clients.navigation.navigateToClientDetail
import com.teraper.printmaster.feature.imports.navigation.importScreens
import com.teraper.printmaster.feature.imports.navigation.navigateToImport
import com.teraper.printmaster.feature.imports.navigation.navigateToImportHistory
import com.teraper.printmaster.feature.imports.navigation.navigateToReviewPayments
import com.teraper.printmaster.feature.more.MoreActions
import com.teraper.printmaster.feature.more.navigation.moreGraph
import com.teraper.printmaster.feature.orders.navigation.OrderEditDestination
import com.teraper.printmaster.feature.orders.navigation.RepairEditDestination
import com.teraper.printmaster.feature.orders.navigation.navigateToNewOrder
import com.teraper.printmaster.feature.orders.navigation.navigateToOrder
import com.teraper.printmaster.feature.orders.navigation.orderScreens
import com.teraper.printmaster.feature.orders.navigation.ordersGraph
import com.teraper.printmaster.feature.payments.navigation.MoneyEntryDestination
import com.teraper.printmaster.feature.payments.navigation.navigateToMoneyEntry
import com.teraper.printmaster.feature.payments.navigation.paymentsGraph
import com.teraper.printmaster.feature.pricelist.navigation.PriceItemEditDestination
import com.teraper.printmaster.feature.pricelist.navigation.navigateToPriceList
import com.teraper.printmaster.feature.pricelist.navigation.priceListScreens
import com.teraper.printmaster.feature.reports.navigation.navigateToDebtExport
import com.teraper.printmaster.feature.reports.navigation.navigateToReports
import com.teraper.printmaster.feature.reports.navigation.reportsScreens
import com.teraper.printmaster.feature.settings.navigation.navigateToBackup
import com.teraper.printmaster.feature.settings.navigation.navigateToSettings
import com.teraper.printmaster.feature.settings.navigation.settingsScreens
import com.teraper.printmaster.feature.team.navigation.navigateToTeam
import com.teraper.printmaster.feature.team.navigation.teamScreens
import com.teraper.printmaster.feature.today.TodayActions
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
        todayScreen(
            TodayActions(
                onOrderClick = navController::navigateToOrder,
                onNewOrder = { navController.navigateToNewOrder() },
                onCashPayment = { navController.navigateToMoneyEntry(0, MoneyEntryKind.CASH_PAYMENT) },
                onOpenDebts = { navController.navigateToTab(TopLevelDestination.PAYMENTS) },
            ),
        )
        clientsGraph(
            navController = navController,
            onRecordPayment = { navController.navigateToMoneyEntry(it, MoneyEntryKind.CASH_PAYMENT) },
            onAddCharge = { navController.navigateToMoneyEntry(it, MoneyEntryKind.MANUAL_CHARGE) },
            onNewOrder = { navController.navigateToNewOrder(clientId = it) },
            onOpenOrder = navController::navigateToOrder,
        )
        ordersGraph(navController)
        orderScreens(navController, onOpenClient = navController::openClientFromOtherTab)
        paymentsGraph(
            navController = navController,
            onOpenClient = navController::openClientFromOtherTab,
            onImport = navController::navigateToImport,
            onReviewPayments = navController::navigateToReviewPayments,
        ) {
            importScreens(navController)
        }
        moreGraph(
            actions = MoreActions(
                onOpenPriceList = navController::navigateToPriceList,
                onOpenCatalog = navController::navigateToCatalog,
                onOpenCompanies = navController::navigateToCompanies,
                onOpenCompany = navController::navigateToCompany,
                onOpenMasters = navController::navigateToMasters,
                onOpenBackup = navController::navigateToBackup,
                onOpenReports = navController::navigateToReports,
                onOpenDebtExport = navController::navigateToDebtExport,
                onOpenSettings = navController::navigateToSettings,
                onOpenCalls = navController::navigateToCalls,
                onOpenTeam = navController::navigateToTeam,
                onOpenImportHistory = {
                    navController.navigateToTab(TopLevelDestination.PAYMENTS)
                    navController.navigateToImportHistory()
                },
            ),
        ) {
            priceListScreens(navController)
            catalogScreens(navController, onOpenClient = navController::openClientFromOtherTab)
            accountScreens(navController)
            settingsScreens(navController)
            reportsScreens(navController, onOpenClient = navController::openClientFromOtherTab)
            callsScreens(navController, onOpenClient = navController::openClientFromOtherTab)
            teamScreens(navController)
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
    CompanyEditDestination::class,
    OrderEditDestination::class,
    PriceItemEditDestination::class,
    RepairEditDestination::class,
)

fun NavDestination?.isFullScreen(): Boolean = this != null && fullScreenRoutes.any { hasRoute(it) }
