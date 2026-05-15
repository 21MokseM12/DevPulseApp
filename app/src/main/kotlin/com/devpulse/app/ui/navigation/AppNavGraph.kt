package com.devpulse.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devpulse.app.OpenUpdatesDigestContextRequest
import com.devpulse.app.ui.auth.AuthRoute
import com.devpulse.app.ui.auth.AuthSuccessEvent
import com.devpulse.app.ui.main.MainUiState
import com.devpulse.app.ui.main.StartupDestination
import com.devpulse.app.ui.settings.QuietHoursScheduleRoute
import com.devpulse.app.ui.settings.SettingsRoute
import com.devpulse.app.ui.subscriptions.SubscriptionsRoute
import com.devpulse.app.ui.testing.SmokeTestTags
import com.devpulse.app.ui.updates.UpdatesRoute

@Composable
fun AppNavGraph(
    uiState: MainUiState,
    onLoginClick: (AuthSuccessEvent) -> Unit,
    onLogoutClick: () -> Unit,
    openUpdatesRequest: Boolean,
    openUpdatesDigestContextRequest: OpenUpdatesDigestContextRequest?,
    onOpenUpdatesHandled: () -> Unit,
    onOpenUpdatesDigestContextHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val nonMainRouteContract = resolveNonMainRouteContract(currentRoute)

    LaunchedEffect(uiState.startupDestination, openUpdatesRequest) {
        val target = resolveStartupRoute(uiState.startupDestination, openUpdatesRequest)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
            if (target == AppRoute.Updates.route) {
                onOpenUpdatesHandled()
            }
        }
    }

    Scaffold(
        topBar = {
            if (nonMainRouteContract != null) {
                BackNavigationTopBar(
                    title = nonMainRouteContract.title,
                    onNavigateBack = { navController.navigateBackWithPolicy(currentRoute) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Splash.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.Splash.route) {
                SplashScreen()
            }

            composable(AppRoute.Auth.route) {
                AuthRoute(
                    onAuthorized = { authSuccess ->
                        onLoginClick(authSuccess)
                    },
                )
            }

            composable(AppRoute.Subscriptions.route) {
                SubscriptionsRoute(
                    onGoToUpdates = { navController.navigateToTopLevel(AppRoute.Updates.route) },
                    onGoToSettings = { navController.navigateToTopLevel(AppRoute.Settings.route) },
                    onLogout = {
                        onLogoutClick()
                    },
                )
            }

            composable(AppRoute.Updates.route) {
                UpdatesRoute(
                    onGoToSubscriptions = { navController.navigateToTopLevel(AppRoute.Subscriptions.route) },
                    onGoToSettings = { navController.navigateToTopLevel(AppRoute.Settings.route) },
                    onLogout = {
                        onLogoutClick()
                    },
                    digestContextRequest = openUpdatesDigestContextRequest,
                    onDigestContextRequestHandled = onOpenUpdatesDigestContextHandled,
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsRoute(
                    onGoToSubscriptions = { navController.navigateToTopLevel(AppRoute.Subscriptions.route) },
                    onGoToUpdates = { navController.navigateToTopLevel(AppRoute.Updates.route) },
                    onOpenQuietHoursSchedule = { navController.navigate(AppRoute.QuietHoursSchedule.route) },
                    onNavigateToAuth = {
                        navController.navigate(AppRoute.Auth.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(AppRoute.QuietHoursSchedule.route) {
                QuietHoursScheduleRoute(
                    onNavigateBack = {
                        navController.navigateBackWithPolicy(AppRoute.QuietHoursSchedule.route)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackNavigationTopBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    androidx.compose.material3.TopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.testTag(SmokeTestTags.NAVIGATION_TOP_BAR_TITLE),
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag(SmokeTestTags.NAVIGATION_BACK_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                )
            }
        },
    )
}

internal fun resolveStartupRoute(
    startupDestination: StartupDestination,
    openUpdatesRequest: Boolean,
): String? {
    return when (startupDestination) {
        StartupDestination.Loading -> null
        StartupDestination.Auth -> AppRoute.Auth.route
        StartupDestination.Subscriptions -> {
            if (openUpdatesRequest) AppRoute.Updates.route else AppRoute.Subscriptions.route
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(AppRoute.Subscriptions.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SplashScreen() {
    CenteredScreenContent {
        CircularProgressIndicator()
        Text(text = "Инициализация...")
    }
}

@Composable
private fun CenteredScreenContent(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
