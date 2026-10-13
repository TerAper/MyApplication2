package com.teraper.printmaster.feature.pricelist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditRoute
import com.teraper.printmaster.feature.pricelist.list.PriceListRoute
import kotlinx.serialization.Serializable

@Serializable
data object PriceListDestination

/** [itemId] 0 = new item for item type [typeId] (0 = general work). */
@Serializable
data class PriceItemEditDestination(val itemId: Long = 0, val typeId: Long = 0)

internal const val ITEM_ID_ARG = "itemId"
internal const val TYPE_ID_ARG = "typeId"

fun NavController.navigateToPriceList() = navigate(PriceListDestination)

/** Price-list screens; the app puts them inside the More tab. */
/** [onOpenCatalogItem] / [onNewCatalogItem] open the Catalog's item form (goods are priced there); wired by the app. */
fun NavGraphBuilder.priceListScreens(navController: NavController, onOpenCatalogItem: (Long) -> Unit, onNewCatalogItem: () -> Unit) {
    composable<PriceListDestination> {
        PriceListRoute(
            onBack = navController::popBackStack,
            onItemClick = { navController.navigate(PriceItemEditDestination(itemId = it)) },
            onAddItem = { navController.navigate(PriceItemEditDestination(typeId = it)) },
            onOpenGoods = onOpenCatalogItem,
            onAddGoods = onNewCatalogItem,
        )
    }
    composable<PriceItemEditDestination> {
        PriceItemEditRoute(onClose = navController::popBackStack)
    }
}
