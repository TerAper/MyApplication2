package com.teraper.printmaster.feature.payments.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.payments.PaymentsRoute
import kotlinx.serialization.Serializable

@Serializable
data object PaymentsDestination

fun NavController.navigateToPayments(navOptions: NavOptions? = null) = navigate(PaymentsDestination, navOptions)

fun NavGraphBuilder.paymentsScreen() {
    composable<PaymentsDestination> { PaymentsRoute() }
}
