package com.teraper.printmaster.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.navigation.PrintMasterNavHost
import com.teraper.printmaster.navigation.TopLevelDestination
import com.teraper.printmaster.navigation.isFullScreen
import com.teraper.printmaster.navigation.navigateToTab

@Composable
fun PrintMasterApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = PmTheme.colors.background,
        bottomBar = {
            if (!currentDestination.isFullScreen()) {
                PrintMasterBottomBar(
                    currentDestination = currentDestination,
                    onTabSelected = { navController.navigateToTab(it) },
                )
            }
        },
    ) { padding ->
        PrintMasterNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun PrintMasterBottomBar(
    currentDestination: NavDestination?,
    onTabSelected: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = PmTheme.colors.surface) {
        TopLevelDestination.entries.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                // Armenian tab names are long; slightly smaller text keeps all five on one line.
                label = {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PmTheme.colors.primary,
                    selectedTextColor = PmTheme.colors.primary,
                    indicatorColor = PmTheme.colors.primaryContainer,
                    unselectedIconColor = PmTheme.colors.inkMuted,
                    unselectedTextColor = PmTheme.colors.inkMuted,
                ),
            )
        }
    }
}
