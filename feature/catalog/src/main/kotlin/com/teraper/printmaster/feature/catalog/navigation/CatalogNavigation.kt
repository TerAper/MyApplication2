package com.teraper.printmaster.feature.catalog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.catalog.item.ItemEditRoute
import com.teraper.printmaster.feature.catalog.list.CatalogRoute
import com.teraper.printmaster.feature.catalog.types.ItemTypesRoute
import com.teraper.printmaster.feature.catalog.types.TypeEditRoute
import kotlinx.serialization.Serializable

@Serializable
data object CatalogDestination

/**
 * [itemId] 0 = add a new item, of type [typeId] when it's known (0 = choose). [intoId]: a new part
 * made from its item's form goes into that item. [barcode]: a scanned code the new item gets;
 * with [receive] the item is received in the warehouse right after saving.
 */
@Serializable
data class ItemEditDestination(val itemId: Long = 0, val typeId: Long = 0, val intoId: Long = 0, val barcode: String = "", val receive: Boolean = false)

@Serializable
data object ItemTypesDestination

/** [typeId] 0 = a new type. */
@Serializable
data class TypeEditDestination(val typeId: Long = 0)

internal const val ITEM_ID_ARG = "itemId"
internal const val TYPE_ID_ARG = "typeId"
internal const val INTO_ID_ARG = "intoId"
internal const val BARCODE_ARG = "barcode"
internal const val RECEIVE_ARG = "receive"

fun NavController.navigateToCatalog() = navigate(CatalogDestination)

fun NavController.navigateToItemTypes() = navigate(ItemTypesDestination)

/**
 * Catalog screens; the app puts them inside the More tab.
 * [onOpenClient] opens a client card (Clients feature), wired by the app.
 */
fun NavGraphBuilder.catalogScreens(
    navController: NavController,
    onOpenClient: (Long) -> Unit,
    onOpenStock: (Long) -> Unit = {},
    onReceive: (Long) -> Unit = {},
) {
    composable<CatalogDestination> {
        CatalogRoute(
            onBack = navController::popBackStack,
            onItemClick = { navController.navigate(ItemEditDestination(it)) },
            onAddItem = { typeId -> navController.navigate(ItemEditDestination(typeId = typeId)) },
            onNewWithBarcode = { code -> navController.navigate(ItemEditDestination(barcode = code)) },
            onOpenTypes = { navController.navigate(ItemTypesDestination) },
        )
    }
    composable<ItemEditDestination> {
        ItemEditRoute(
            onClose = navController::popBackStack,
            onOpenClient = onOpenClient,
            onOpenItem = { itemId, typeId, intoId -> navController.navigate(ItemEditDestination(itemId, typeId, intoId)) },
            onOpenStock = onOpenStock,
            onReceive = onReceive,
        )
    }
    composable<ItemTypesDestination> {
        ItemTypesRoute(
            onBack = navController::popBackStack,
            onTypeClick = { navController.navigate(TypeEditDestination(it)) },
            onAddType = { navController.navigate(TypeEditDestination()) },
        )
    }
    composable<TypeEditDestination> {
        TypeEditRoute(onClose = navController::popBackStack)
    }
}
