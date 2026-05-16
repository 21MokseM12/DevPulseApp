package com.devpulse.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import com.devpulse.app.ui.theme.Spacing
import com.devpulse.app.ui.updates.UpdatesRoute

private data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val testTag: String,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(
            route = AppRoute.Subscriptions.route,
            icon = Icons.Filled.Home,
            label = "Subscriptions",
            testTag = SmokeTestTags.UPDATES_OPEN_SUBSCRIPTIONS_BUTTON,
        ),
        BottomNavItem(
            route = AppRoute.Updates.route,
            icon = Icons.Filled.Notifications,
            label = "Updates",
            testTag = SmokeTestTags.SUBSCRIPTIONS_OPEN_UPDATES_BUTTON,
        ),
        BottomNavItem(
            route = AppRoute.Settings.route,
            icon = Icons.Filled.Settings,
            label = "Settings",
            testTag = SmokeTestTags.SUBSCRIPTIONS_OPEN_SETTINGS_BUTTON,
        ),
    )

private val topLevelRoutes = bottomNavItems.map { it.route }.toSet()

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
    val isTopLevel = currentRoute in topLevelRoutes

    LaunchedEffect(uiState.startupDestination, openUpdatesRequest) {
        val target = resolveStartupRoute(uiState.startupDestination, openUpdatesRequest)
        if (target != null) {
            navController.navigate(target) {
                popUpTo(AppRoute.Splash.route) { inclusive = true }
                launchSingleTop = true
            }
            if (target == AppRoute.Updates.route) {
                onOpenUpdatesHandled()
            }
        }
    }

    Scaffold(
        topBar = {
            when {
                nonMainRouteContract != null ->
                    BackNavigationTopBar(
                        title = nonMainRouteContract.title,
                        onNavigateBack = { navController.navigateBackWithPolicy(currentRoute) },
                    )

                isTopLevel ->
                    MainTopAppBar(
                        currentRoute = currentRoute,
                        onLogoutClick = onLogoutClick,
                    )

                else -> Unit
            }
        },
        bottomBar = {
            if (isTopLevel) {
                DevPulseBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTopLevel(route) },
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
                    onLogout = { onLogoutClick() },
                )
            }

            composable(AppRoute.Updates.route) {
                UpdatesRoute(
                    onGoToSubscriptions = { navController.navigateToTopLevel(AppRoute.Subscriptions.route) },
                    onGoToSettings = { navController.navigateToTopLevel(AppRoute.Settings.route) },
                    onLogout = { onLogoutClick() },
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
                            popUpTo(AppRoute.Splash.route) { inclusive = true }
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
private fun MainTopAppBar(
    currentRoute: String?,
    onLogoutClick: () -> Unit,
) {
    val (title, titleTag) =
        when (currentRoute) {
            AppRoute.Subscriptions.route -> "Subscriptions" to SmokeTestTags.SUBSCRIPTIONS_TITLE
            AppRoute.Updates.route -> "Updates" to SmokeTestTags.UPDATES_TITLE
            AppRoute.Settings.route -> "Settings" to SmokeTestTags.SETTINGS_TITLE
            else -> "DevPulse" to null
        }
    val showLogoutAction = currentRoute == AppRoute.Subscriptions.route

    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
            )
        },
        actions = {
            if (showLogoutAction) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_LOGOUT_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Выйти",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackNavigationTopBar(
    title: String,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
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
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
    )
}

@Composable
private fun DevPulseBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(text = item.label) },
                modifier = Modifier.testTag(item.testTag),
            )
        }
    }
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
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "DevPulse",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
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
