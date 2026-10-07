package com.teraper.printmaster.feature.catalog.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.catalog.list.CatalogRoute
import com.teraper.printmaster.feature.catalog.model.ModelEditRoute
import kotlinx.serialization.Serializable

@Serializable
data object CatalogDestination

/** [modelId] 0 = add a new model. */
@Serializable
data class ModelEditDestination(val modelId: Long = 0)

internal const val MODEL_ID_ARG = "modelId"

fun NavController.navigateToCatalog() = navigate(CatalogDestination)

/**
 * Catalog screens; the app puts them inside the More tab.
 * [onOpenClient] opens a client card (Clients feature), wired by the app.
 */
fun NavGraphBuilder.catalogScreens(navController: NavController, onOpenClient: (Long) -> Unit) {
    composable<CatalogDestination> {
        CatalogRoute(
            onBack = navController::popBackStack,
            onModelClick = { navController.navigate(ModelEditDestination(it)) },
            onAddModel = { navController.navigate(ModelEditDestination()) },
        )
    }
    composable<ModelEditDestination> {
        ModelEditRoute(onClose = navController::popBackStack, onOpenClient = onOpenClient)
    }
}
