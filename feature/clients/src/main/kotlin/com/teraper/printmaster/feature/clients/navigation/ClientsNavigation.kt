package com.teraper.printmaster.feature.clients.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.teraper.printmaster.feature.clients.detail.ClientDetailRoute
import com.teraper.printmaster.feature.clients.edit.ClientEditRoute
import com.teraper.printmaster.feature.clients.list.ClientListRoute
import com.teraper.printmaster.feature.clients.printer.PrinterEditRoute
import kotlinx.serialization.Serializable

/** The whole Clients tab (list + card + form), so the tab stays highlighted on every screen of it. */
@Serializable
data object ClientsGraph

@Serializable
data object ClientListDestination

@Serializable
data class ClientDetailDestination(val clientId: Long)

/** [clientId] 0 = add a new client. */
@Serializable
data class ClientEditDestination(val clientId: Long = 0)

/** [printerId] 0 = add a printer to the client. */
@Serializable
data class PrinterEditDestination(val clientId: Long, val printerId: Long = 0)

internal const val CLIENT_ID_ARG = "clientId"
internal const val PRINTER_ID_ARG = "printerId"

fun NavController.navigateToClients(navOptions: NavOptions? = null) = navigate(ClientsGraph, navOptions)

fun NavController.navigateToNewClient() = navigate(ClientEditDestination())

fun NavController.navigateToClientDetail(clientId: Long) = navigate(ClientDetailDestination(clientId))

/**
 * [onRecordPayment] / [onAddCharge] open the Payments feature's entry screen;
 * the app wires them so this feature doesn't depend on Payments.
 */
fun NavGraphBuilder.clientsGraph(
    navController: NavController,
    onRecordPayment: (clientId: Long) -> Unit,
    onAddCharge: (clientId: Long) -> Unit,
) {
    navigation<ClientsGraph>(startDestination = ClientListDestination) {
        composable<ClientListDestination> {
            ClientListRoute(
                onClientClick = { navController.navigate(ClientDetailDestination(it)) },
                onAddClient = { navController.navigate(ClientEditDestination()) },
            )
        }
        composable<ClientDetailDestination> {
            ClientDetailRoute(
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(ClientEditDestination(it)) },
                onRecordPayment = onRecordPayment,
                onAddCharge = onAddCharge,
                onAddPrinter = { navController.navigate(PrinterEditDestination(it)) },
                onPrinterClick = { clientId, printerId -> navController.navigate(PrinterEditDestination(clientId, printerId)) },
            )
        }
        composable<PrinterEditDestination> {
            PrinterEditRoute(onClose = navController::popBackStack)
        }
        composable<ClientEditDestination> {
            ClientEditRoute(
                onClose = navController::popBackStack,
                onSaved = { clientId, wasNew ->
                    if (wasNew) {
                        // New client: show its card instead of going back to the list.
                        navController.navigate(ClientDetailDestination(clientId)) {
                            popUpTo<ClientEditDestination> { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
