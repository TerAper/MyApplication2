package com.teraper.printmaster.feature.warehouse.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.warehouse.item.ItemStockRoute
import com.teraper.printmaster.feature.warehouse.list.WarehouseRoute
import kotlinx.serialization.Serializable

@Serializable
data object WarehouseDestination

/** One catalog item's stock: count, receive/adjust/move and history. */
@Serializable
data class ItemStockDestination(val itemId: Long, val receive: Boolean = false)

internal const val ITEM_ID_ARG = "itemId"
internal const val RECEIVE_ARG = "receive"

fun NavController.navigateToWarehouse() = navigate(WarehouseDestination)

fun NavController.navigateToItemStock(itemId: Long, receive: Boolean = false, builder: NavOptionsBuilder.() -> Unit = {}) =
    navigate(ItemStockDestination(itemId, receive), builder)

/** Warehouse screens; [onOpenCatalogItem] opens the item in the Catalog, wired by the app. */
fun NavGraphBuilder.warehouseScreens(navController: NavController, onOpenCatalogItem: (Long) -> Unit, onNewItem: (barcode: String) -> Unit = {}) {
    composable<WarehouseDestination> {
        WarehouseRoute(onBack = navController::popBackStack, onItemClick = { navController.navigate(ItemStockDestination(it)) }, onNewItem = onNewItem)
    }
    composable<ItemStockDestination> {
        ItemStockRoute(onBack = navController::popBackStack, onOpenCatalogItem = onOpenCatalogItem)
    }
}
