package com.teraper.printmaster.feature.pricelist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.feature.pricelist.edit.PriceItemEditRoute
import com.teraper.printmaster.feature.pricelist.list.PriceListRoute
import kotlinx.serialization.Serializable

@Serializable
data object PriceListDestination

/** [itemId] 0 = new item in [category] (a [RepairCategory] name). */
@Serializable
data class PriceItemEditDestination(val itemId: Long = 0, val category: String = RepairCategory.CARTRIDGE.name)

internal const val ITEM_ID_ARG = "itemId"
internal const val CATEGORY_ARG = "category"

fun NavController.navigateToPriceList() = navigate(PriceListDestination)

/** Price-list screens; the app puts them inside the More tab. */
fun NavGraphBuilder.priceListScreens(navController: NavController) {
    composable<PriceListDestination> {
        PriceListRoute(
            onBack = navController::popBackStack,
            onItemClick = { navController.navigate(PriceItemEditDestination(itemId = it)) },
            onAddItem = { navController.navigate(PriceItemEditDestination(category = it.name)) },
        )
    }
    composable<PriceItemEditDestination> {
        PriceItemEditRoute(onClose = navController::popBackStack)
    }
}
