package com.teraper.printmaster.feature.account.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.teraper.printmaster.feature.account.companies.CompaniesRoute
import com.teraper.printmaster.feature.account.companies.CompanyEditRoute
import com.teraper.printmaster.feature.account.masters.MastersRoute
import kotlinx.serialization.Serializable

@Serializable
data object CompaniesDestination

/** [companyId] 0 = add a new company. */
@Serializable
data class CompanyEditDestination(val companyId: Long = 0)

@Serializable
data object MastersDestination

internal const val COMPANY_ID_ARG = "companyId"

fun NavController.navigateToCompanies() = navigate(CompaniesDestination)

fun NavController.navigateToCompany(companyId: Long) = navigate(CompanyEditDestination(companyId))

fun NavController.navigateToMasters() = navigate(MastersDestination)

/** Company and master settings; the app nests them in the More tab. */
fun NavGraphBuilder.accountScreens(navController: NavController) {
    composable<CompaniesDestination> {
        CompaniesRoute(
            onBack = navController::popBackStack,
            onCompanyClick = { navController.navigate(CompanyEditDestination(it)) },
            onAddCompany = { navController.navigate(CompanyEditDestination()) },
        )
    }
    composable<CompanyEditDestination> { CompanyEditRoute(onClose = navController::popBackStack) }
    composable<MastersDestination> { MastersRoute(onBack = navController::popBackStack) }
}
