package com.teraper.printmaster.feature.payments.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.feature.payments.entry.MoneyEntryRoute
import com.teraper.printmaster.feature.payments.overview.PaymentsOverviewRoute
import kotlinx.serialization.Serializable

/** The whole Payments tab, so the tab stays highlighted on every screen of it. */
@Serializable
data object PaymentsGraph

@Serializable
data object PaymentsOverviewDestination

/** Cash payment or new debt. [clientId] 0 = pick the client on the screen. */
@Serializable
data class MoneyEntryDestination(val clientId: Long = 0, val isCharge: Boolean = false)

internal const val CLIENT_ID_ARG = "clientId"
internal const val IS_CHARGE_ARG = "isCharge"

/** Used by other features, e.g. the client card's "Cash payment" button. */
fun NavController.navigateToMoneyEntry(clientId: Long, kind: MoneyEntryKind) =
    navigate(MoneyEntryDestination(clientId, isCharge = kind == MoneyEntryKind.MANUAL_CHARGE))

/** [nestedGraphs] adds other features' screens (Excel import) inside this tab. */
fun NavGraphBuilder.paymentsGraph(
    navController: NavController,
    onOpenClient: (Long) -> Unit,
    onImport: () -> Unit,
    onReviewPayments: () -> Unit,
    nestedGraphs: NavGraphBuilder.() -> Unit = {},
) {
    navigation<PaymentsGraph>(startDestination = PaymentsOverviewDestination) {
        composable<PaymentsOverviewDestination> {
            PaymentsOverviewRoute(
                onOpenClient = onOpenClient,
                onCashPayment = { navController.navigate(MoneyEntryDestination()) },
                onImport = onImport,
                onReviewPayments = onReviewPayments,
            )
        }
        composable<MoneyEntryDestination> {
            MoneyEntryRoute(onClose = navController::popBackStack)
        }
        nestedGraphs()
    }
}
