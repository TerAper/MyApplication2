package com.teraper.printmaster.feature.orders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.orders.OrdersRoute
import kotlinx.serialization.Serializable

@Serializable
data object OrdersDestination

fun NavController.navigateToOrders(navOptions: NavOptions? = null) = navigate(OrdersDestination, navOptions)

fun NavGraphBuilder.ordersScreen() {
    composable<OrdersDestination> { OrdersRoute() }
}
