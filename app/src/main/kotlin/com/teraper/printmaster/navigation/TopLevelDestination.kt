package com.teraper.printmaster.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.teraper.printmaster.R
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.feature.clients.navigation.ClientsDestination
import com.teraper.printmaster.feature.more.navigation.MoreDestination
import com.teraper.printmaster.feature.orders.navigation.OrdersDestination
import com.teraper.printmaster.feature.payments.navigation.PaymentsDestination
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
    CLIENTS(PmIcons.Clients, R.string.tab_clients, ClientsDestination, ClientsDestination::class),
    ORDERS(PmIcons.Orders, R.string.tab_orders, OrdersDestination, OrdersDestination::class),
    PAYMENTS(PmIcons.Payments, R.string.tab_payments, PaymentsDestination, PaymentsDestination::class),
    MORE(PmIcons.More, R.string.tab_more, MoreDestination, MoreDestination::class),
}
