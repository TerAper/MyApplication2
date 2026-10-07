package com.teraper.printmaster.feature.orders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.teraper.printmaster.feature.orders.detail.OrderDetailRoute
import com.teraper.printmaster.feature.orders.edit.OrderEditRoute
import com.teraper.printmaster.feature.orders.list.OrdersRoute
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** The whole Orders tab. */
@Serializable
data object OrdersGraph

@Serializable
data object OrdersListDestination

@Serializable
data class OrderDetailDestination(val orderId: Long)

/**
 * [orderId] 0 = new order; then [clientId] (0 = choose) and [dateEpochDay]
 * ([NO_DATE] = today) can preset the form.
 */
@Serializable
data class OrderEditDestination(val orderId: Long = 0, val clientId: Long = 0, val dateEpochDay: Long = NO_DATE)

internal const val ORDER_ID_ARG = "orderId"
internal const val CLIENT_ID_ARG = "clientId"
internal const val DATE_ARG = "dateEpochDay"
const val NO_DATE = Long.MIN_VALUE

fun NavController.navigateToOrders(navOptions: NavOptions? = null) = navigate(OrdersGraph, navOptions)

fun NavController.navigateToOrder(orderId: Long) = navigate(OrderDetailDestination(orderId))

fun NavController.navigateToNewOrder(clientId: Long = 0, date: LocalDate? = null) =
    navigate(OrderEditDestination(clientId = clientId, dateEpochDay = date?.toEpochDay() ?: NO_DATE))

/** The Orders tab itself: the day list. */
fun NavGraphBuilder.ordersGraph(navController: NavController) {
    navigation<OrdersGraph>(startDestination = OrdersListDestination) {
        composable<OrdersListDestination> {
            OrdersRoute(
                onOrderClick = navController::navigateToOrder,
                onNewOrder = { date -> navController.navigateToNewOrder(date = date) },
            )
        }
    }
}

/**
 * Order detail and form, outside any tab, so Today, the client card and the Orders tab
 * can all open them on top of their own screens. [onOpenClient] opens the client card.
 */
fun NavGraphBuilder.orderScreens(navController: NavController, onOpenClient: (Long) -> Unit) {
    composable<OrderDetailDestination> {
        OrderDetailRoute(
            onBack = navController::popBackStack,
            onEdit = { navController.navigate(OrderEditDestination(orderId = it)) },
            onOpenClient = onOpenClient,
        )
    }
    composable<OrderEditDestination> {
        OrderEditRoute(
            onClose = navController::popBackStack,
            onSaved = { orderId, wasNew ->
                if (wasNew) {
                    navController.navigate(OrderDetailDestination(orderId)) {
                        popUpTo<OrderEditDestination> { inclusive = true }
                    }
                } else {
                    navController.popBackStack()
                }
            },
        )
    }
}
