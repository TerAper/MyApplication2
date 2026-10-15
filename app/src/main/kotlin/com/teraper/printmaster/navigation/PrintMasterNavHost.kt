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
import com.teraper.printmaster.feature.catalog.navigation.ItemEditDestination
import com.teraper.printmaster.feature.catalog.navigation.TypeEditDestination
import com.teraper.printmaster.feature.catalog.navigation.catalogScreens
import com.teraper.printmaster.feature.catalog.navigation.navigateToCatalog
import com.teraper.printmaster.feature.catalog.navigation.navigateToItemTypes
import com.teraper.printmaster.feature.clients.navigation.ClientEditDestination
import com.teraper.printmaster.feature.clients.navigation.ClientItemEditDestination
import com.teraper.printmaster.feature.clients.navigation.clientsGraph
import com.teraper.printmaster.feature.clients.navigation.navigateToClientDetail
import com.teraper.printmaster.feature.expenses.navigation.ExpenseEditDestination
import com.teraper.printmaster.feature.expenses.navigation.expensesScreens
import com.teraper.printmaster.feature.expenses.navigation.navigateToExpenses
import com.teraper.printmaster.feature.expenses.navigation.navigateToNewExpense
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
import com.teraper.printmaster.feature.warehouse.navigation.navigateToItemStock
import com.teraper.printmaster.feature.warehouse.navigation.navigateToWarehouse
import com.teraper.printmaster.feature.warehouse.navigation.warehouseScreens
import com.teraper.printmaster.feature.reports.navigation.navigateToDebtExport
import com.teraper.printmaster.feature.reports.navigation.navigateToReports
import com.teraper.printmaster.feature.reports.navigation.reportsScreens
import com.teraper.printmaster.feature.settings.navigation.navigateToBackup
import com.teraper.printmaster.feature.settings.navigation.navigateToExcelColumns
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
                onAddExpense = navController::navigateToNewExpense,
                onOpenDebts = { navController.navigateToTab(TopLevelDestination.PAYMENTS) },
            ),
        )
        clientsGraph(
            navController = navController,
            onRecordPayment = { navController.navigateToMoneyEntry(it, MoneyEntryKind.CASH_PAYMENT) },
            onAddCharge = { navController.navigateToMoneyEntry(it, MoneyEntryKind.MANUAL_CHARGE) },
            onNewOrder = { navController.navigateToNewOrder(clientId = it) },
            onOpenOrder = navController::navigateToOrder,
            onOpenItemTypes = navController::navigateToItemTypes,
        )
        ordersGraph(navController)
        orderScreens(navController, onOpenClient = navController::openClientFromOtherTab)
        paymentsGraph(
            navController = navController,
            onOpenClient = navController::openClientFromOtherTab,
            onImport = navController::navigateToImport,
            onReviewPayments = navController::navigateToReviewPayments,
        ) {
            importScreens(navController, onOpenColumns = navController::navigateToExcelColumns)
        }
        moreGraph(
            actions = MoreActions(
                onOpenPriceList = navController::navigateToPriceList,
                onOpenCatalog = navController::navigateToCatalog,
                onOpenWarehouse = navController::navigateToWarehouse,
                onOpenCompanies = navController::navigateToCompanies,
                onOpenCompany = navController::navigateToCompany,
                onOpenMasters = navController::navigateToMasters,
                onOpenBackup = navController::navigateToBackup,
                onOpenReports = navController::navigateToReports,
                onOpenDebtExport = navController::navigateToDebtExport,
                onOpenSettings = navController::navigateToSettings,
                onOpenCalls = navController::navigateToCalls,
                onOpenTeam = navController::navigateToTeam,
                onOpenExpenses = navController::navigateToExpenses,
                onOpenImportHistory = {
                    navController.navigateToTab(TopLevelDestination.PAYMENTS)
                    navController.navigateToImportHistory()
                },
            ),
        ) {
            priceListScreens(
                navController,
                onOpenCatalogItem = { navController.navigate(ItemEditDestination(itemId = it)) },
                onNewCatalogItem = { navController.navigate(ItemEditDestination()) },
            )
            catalogScreens(
                navController,
                onOpenClient = navController::openClientFromOtherTab,
                onOpenStock = { navController.navigateToItemStock(it) },
                // Added from a scanned code: straight on to receiving it, the form closed.
                onReceive = { itemId ->
                    navController.navigateToItemStock(itemId, receive = true) { popUpTo<ItemEditDestination> { inclusive = true } }
                },
            )
            warehouseScreens(
                navController,
                onOpenCatalogItem = { navController.navigate(ItemEditDestination(itemId = it)) },
                onNewItem = { code -> navController.navigate(ItemEditDestination(barcode = code, receive = true)) },
            )
            accountScreens(navController)
            settingsScreens(navController)
            reportsScreens(navController, onOpenClient = navController::openClientFromOtherTab, onOpenExpenses = navController::navigateToExpenses)
            callsScreens(navController, onOpenClient = navController::openClientFromOtherTab)
            teamScreens(navController)
            expensesScreens(navController)
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
    ClientItemEditDestination::class,
    MoneyEntryDestination::class,
    ItemEditDestination::class,
    TypeEditDestination::class,
    CompanyEditDestination::class,
    OrderEditDestination::class,
    PriceItemEditDestination::class,
    RepairEditDestination::class,
    ExpenseEditDestination::class,
)

fun NavDestination?.isFullScreen(): Boolean = this != null && fullScreenRoutes.any { hasRoute(it) }
