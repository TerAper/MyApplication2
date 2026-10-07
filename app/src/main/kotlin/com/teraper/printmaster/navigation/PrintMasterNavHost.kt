package com.teraper.printmaster.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.teraper.printmaster.feature.clients.navigation.clientsScreen
import com.teraper.printmaster.feature.more.navigation.moreScreen
import com.teraper.printmaster.feature.orders.navigation.ordersScreen
import com.teraper.printmaster.feature.payments.navigation.paymentsScreen
import com.teraper.printmaster.feature.today.navigation.TodayDestination
import com.teraper.printmaster.feature.today.navigation.todayScreen

/**
 * The only place that knows about every feature. Links between features
 * (e.g. visit → cash payment) are wired here, so features stay independent.
 */
@Composable
fun PrintMasterNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TodayDestination,
        modifier = modifier,
    ) {
        todayScreen()
        clientsScreen()
        ordersScreen()
        paymentsScreen()
        moreScreen()
    }
}
