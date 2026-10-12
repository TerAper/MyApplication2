package com.teraper.printmaster.feature.clients.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.teraper.printmaster.feature.clients.detail.ClientDetailRoute
import com.teraper.printmaster.feature.clients.edit.ClientEditRoute
import com.teraper.printmaster.feature.clients.entry.EntryDetailRoute
import com.teraper.printmaster.feature.clients.list.ClientListRoute
import com.teraper.printmaster.feature.clients.item.ClientItemEditRoute
import com.teraper.printmaster.feature.clients.serial.SerialSearchRoute
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

/** [clientItemId] 0 = add something the client owns. */
@Serializable
data class ClientItemEditDestination(val clientId: Long, val clientItemId: Long = 0)

/** Which client item has this serial ([serial] scanned or empty to type), and its orders. */
@Serializable
data class SerialSearchDestination(val serial: String = "")

internal const val SERIAL_ARG = "serial"

/** One row of a client's money history: a charge ([isCharge]) or a payment, with all its data. */
@Serializable
data class EntryDetailDestination(val isCharge: Boolean, val id: Long)

internal const val CLIENT_ID_ARG = "clientId"
internal const val ENTRY_ID_ARG = "id"
internal const val IS_CHARGE_ARG = "isCharge"
internal const val CLIENT_ITEM_ID_ARG = "clientItemId"

fun NavController.navigateToClients(navOptions: NavOptions? = null) = navigate(ClientsGraph, navOptions)

fun NavController.navigateToNewClient() = navigate(ClientEditDestination())

fun NavController.navigateToClientDetail(clientId: Long) = navigate(ClientDetailDestination(clientId))

/**
 * [onRecordPayment] / [onAddCharge] open the Payments feature's entry screen and
 * [onNewOrder] / [onOpenOrder] the Orders feature, [onOpenItemTypes] the Catalog's types; the app
 * wires them so features stay independent.
 */
fun NavGraphBuilder.clientsGraph(
    navController: NavController,
    onRecordPayment: (clientId: Long) -> Unit,
    onAddCharge: (clientId: Long) -> Unit,
    onNewOrder: (clientId: Long) -> Unit,
    onOpenOrder: (orderId: Long) -> Unit,
    onOpenItemTypes: () -> Unit,
) {
    navigation<ClientsGraph>(startDestination = ClientListDestination) {
        composable<ClientListDestination> {
            ClientListRoute(
                onClientClick = { navController.navigate(ClientDetailDestination(it)) },
                onAddClient = { navController.navigate(ClientEditDestination()) },
                onSearchSerial = { navController.navigate(SerialSearchDestination(it)) },
            )
        }
        composable<ClientDetailDestination> {
            ClientDetailRoute(
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(ClientEditDestination(it)) },
                onRecordPayment = onRecordPayment,
                onAddCharge = onAddCharge,
                onAddItem = { navController.navigate(ClientItemEditDestination(it)) },
                onItemClick = { clientId, itemId -> navController.navigate(ClientItemEditDestination(clientId, itemId)) },
                onNewOrder = onNewOrder,
                onOrderClick = onOpenOrder,
                onOpenEntry = { isCharge, id -> navController.navigate(EntryDetailDestination(isCharge, id)) },
            )
        }
        composable<EntryDetailDestination> {
            EntryDetailRoute(
                onBack = navController::popBackStack,
                onOpenEntry = { isCharge, id -> navController.navigate(EntryDetailDestination(isCharge, id)) },
                onOpenOrder = onOpenOrder,
            )
        }
        composable<SerialSearchDestination> {
            SerialSearchRoute(
                onBack = navController::popBackStack,
                onOpenClient = { navController.navigate(ClientDetailDestination(it)) },
                onOpenOrder = onOpenOrder,
            )
        }
        composable<ClientItemEditDestination> {
            ClientItemEditRoute(onClose = navController::popBackStack, onOpenItemTypes = onOpenItemTypes)
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
