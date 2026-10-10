package com.teraper.printmaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teraper.printmaster.core.designsystem.component.LocalShowMoney
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.feature.account.navigation.navigateToCompanies
import com.teraper.printmaster.feature.account.onboarding.OnboardingRoute
import com.teraper.printmaster.feature.team.signin.SignInRoute
import com.teraper.printmaster.navigation.PrintMasterNavHost
import com.teraper.printmaster.navigation.TopLevelDestination
import com.teraper.printmaster.navigation.currentTab
import com.teraper.printmaster.navigation.isFullScreen
import com.teraper.printmaster.navigation.navigateToTab

@Composable
fun PrintMasterApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        AppUiState.Loading -> Box(Modifier.fillMaxSize().background(PmTheme.colors.background))
        is AppUiState.NeedsSignIn -> SignInRoute(onSkip = if (current.canSkipForTest) viewModel::onSkipSignInForTest else null)
        AppUiState.NeedsRegistration -> OnboardingRoute()
        is AppUiState.Ready -> MainApp(current, onCompanySelected = viewModel::onCompanySelected)
    }
}

@Composable
private fun MainApp(
    state: AppUiState.Ready,
    onCompanySelected: (Long) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    var showSwitch by rememberSaveable { mutableStateOf(false) }
    // A master who joined a company is told about new orders: ask once for notifications.
    if (state.mode == AccountMode.JOINED) AskForNotifications()
    val fullScreen = currentDestination.isFullScreen()
    val showMoney = state.mode != AccountMode.JOINED
    // A joined master has no money to manage: no Payments tab.
    val tabs = TopLevelDestination.entries.filter { showMoney || it != TopLevelDestination.PAYMENTS }

    CompositionLocalProvider(LocalShowMoney provides showMoney) {
    Scaffold(
        containerColor = PmTheme.colors.background,
        topBar = {
            val company = state.activeCompany
            if (state.showCompanySwitch && company != null && !fullScreen) {
                CompanyStrip(company, onClick = { showSwitch = true })
            }
        },
        bottomBar = {
            if (!fullScreen) {
                PrintMasterBottomBar(
                    tabs = tabs,
                    currentTab = backStack.currentTab(),
                    onTabSelected = { navController.navigateToTab(it) },
                )
            }
        },
    ) { padding ->
        PrintMasterNavHost(navController = navController, modifier = Modifier.padding(padding))
    }
    }

    if (showSwitch) {
        CompanySwitchSheet(
            companies = state.companies,
            activeId = state.activeCompany?.id,
            onSelect = {
                onCompanySelected(it)
                showSwitch = false
            },
            onManage = {
                showSwitch = false
                navController.navigateToTab(TopLevelDestination.MORE)
                navController.navigateToCompanies()
            },
            onDismiss = { showSwitch = false },
        )
    }
}

@Composable
private fun PrintMasterBottomBar(
    tabs: List<TopLevelDestination>,
    currentTab: TopLevelDestination?,
    onTabSelected: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = PmTheme.colors.surface) {
        tabs.forEach { tab ->
            val selected = tab == currentTab
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

/** Android 13+ needs the user's yes before any notification; asked once per launch until answered. */
@Composable
internal fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
