package com.devpulse.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.devpulse.app.ui.auth.AuthRoute
import com.devpulse.app.ui.main.MainUiState
import com.devpulse.app.ui.main.StartupDestination

@Composable
fun AppNavGraph(
    uiState: MainUiState,
    onLoginClick: (String) -> Unit,
    onLogoutClick: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(uiState.startupDestination) {
        val target =
            when (uiState.startupDestination) {
                StartupDestination.Loading -> null
                StartupDestination.Auth -> AppRoute.Auth.route
                StartupDestination.Subscriptions -> AppRoute.Subscriptions.route
            }
        if (target != null) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold { innerPadding ->
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
                    onAuthorized = { login ->
                        onLoginClick(login)
                    },
                )
            }

            composable(AppRoute.Subscriptions.route) {
                SubscriptionsScreen(
                    onGoToUpdates = { navController.navigateToTopLevel(AppRoute.Updates.route) },
                    onGoToSettings = { navController.navigateToTopLevel(AppRoute.Settings.route) },
                    onLogout = {
                        onLogoutClick()
                    },
                )
            }

            composable(AppRoute.Updates.route) {
                UpdatesScreen(
                    onGoToSubscriptions = { navController.navigateToTopLevel(AppRoute.Subscriptions.route) },
                    onGoToSettings = { navController.navigateToTopLevel(AppRoute.Settings.route) },
                    onLogout = {
                        onLogoutClick()
                    },
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    onGoToSubscriptions = { navController.navigateToTopLevel(AppRoute.Subscriptions.route) },
                    onGoToUpdates = { navController.navigateToTopLevel(AppRoute.Updates.route) },
                    onLogout = {
                        onLogoutClick()
                    },
                )
            }
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
private fun SubscriptionsScreen(
    onGoToUpdates: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    CenteredScreenContent {
        Text(text = "Subscriptions")
        Button(onClick = onGoToUpdates) { Text(text = "Открыть Updates") }
        Button(onClick = onGoToSettings) { Text(text = "Открыть Settings") }
        Button(onClick = onLogout) { Text(text = "Выйти") }
    }
}

@Composable
private fun UpdatesScreen(
    onGoToSubscriptions: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    CenteredScreenContent {
        Text(text = "Updates")
        Button(onClick = onGoToSubscriptions) { Text(text = "Открыть Subscriptions") }
        Button(onClick = onGoToSettings) { Text(text = "Открыть Settings") }
        Button(onClick = onLogout) { Text(text = "Выйти") }
    }
}

@Composable
private fun SettingsScreen(
    onGoToSubscriptions: () -> Unit,
    onGoToUpdates: () -> Unit,
    onLogout: () -> Unit,
) {
    CenteredScreenContent {
        Text(text = "Settings")
        Button(onClick = onGoToSubscriptions) { Text(text = "Открыть Subscriptions") }
        Button(onClick = onGoToUpdates) { Text(text = "Открыть Updates") }
        Button(onClick = onLogout) { Text(text = "Выйти") }
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
