package com.devpulse.app.ui.navigation

import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackNavigationContractIntegrationTest {
    @Test
    fun navigateBackWithPolicy_popsToSettings_whenRouteExistsInBackStack() {
        runOnMainSync {
            val navController = createNavController(startDestination = AppRoute.Subscriptions.route)
            navController.navigate(AppRoute.Settings.route)
            navController.navigate(AppRoute.QuietHoursSchedule.route)

            navController.navigateBackWithPolicy(AppRoute.QuietHoursSchedule.route)

            assertEquals(AppRoute.Settings.route, navController.currentBackStackEntry?.destination?.route)
        }
    }

    @Test
    fun navigateBackWithPolicy_navigatesToSettings_whenOpenedFromDeepLink() {
        runOnMainSync {
            val navController = createNavController(startDestination = AppRoute.QuietHoursSchedule.route)

            navController.navigateBackWithPolicy(AppRoute.QuietHoursSchedule.route)

            assertEquals(AppRoute.Settings.route, navController.currentBackStackEntry?.destination?.route)
        }
    }

    private fun createNavController(startDestination: String): NavHostController {
        val context: Context = ApplicationProvider.getApplicationContext()
        val navController = NavHostController(context)
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.navigatorProvider.addNavigator(DialogNavigator())
        navController.graph =
            navController.createGraph(startDestination = startDestination) {
                composable(AppRoute.Subscriptions.route) {}
                composable(AppRoute.Updates.route) {}
                composable(AppRoute.Settings.route) {}
                composable(AppRoute.QuietHoursSchedule.route) {}
            }
        return navController
    }

    private fun runOnMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
