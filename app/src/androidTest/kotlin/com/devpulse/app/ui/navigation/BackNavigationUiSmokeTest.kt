package com.devpulse.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devpulse.app.ui.testing.SmokeTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackNavigationUiSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nonMainBackViaNavigationIcon_returnsToMainScreen() {
        setSmokeContent()
        openNonMainScreen()

        composeRule.onNodeWithTag(SmokeTestTags.NAVIGATION_BACK_BUTTON).performClick()

        assertReturnedToMain()
    }

    @Test
    fun nonMainBackViaHardwareButton_returnsToMainScreen() {
        setSmokeContent()
        openNonMainScreen()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        assertReturnedToMain()
    }

    private fun setSmokeContent() {
        composeRule.setContent {
            BackNavigationSmokeHarness()
        }
    }

    private fun openNonMainScreen() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SETTINGS_TITLE)
        composeRule.onNodeWithTag(SmokeTestTags.SETTINGS_OPEN_QUIET_HOURS_BUTTON).performClick()
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.NAVIGATION_BACK_BUTTON)
    }

    private fun assertReturnedToMain() {
        composeRule.waitUntilNodeWithTagExists(SmokeTestTags.SETTINGS_TITLE)
        composeRule.waitUntilNodeWithTagMissing(SmokeTestTags.NAVIGATION_BACK_BUTTON)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BackNavigationSmokeHarness() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val nonMainRouteContract = resolveNonMainRouteContract(currentRoute)

    Scaffold(
        topBar = {
            if (nonMainRouteContract != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = nonMainRouteContract.title,
                            modifier = Modifier.testTag(SmokeTestTags.NAVIGATION_TOP_BAR_TITLE),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateBackWithPolicy(currentRoute) },
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Settings.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoute.Settings.route) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    Text(
                        text = "Settings",
                        modifier = Modifier.testTag(SmokeTestTags.SETTINGS_TITLE),
                    )
                    Button(
                        onClick = { navController.navigate(AppRoute.QuietHoursSchedule.route) },
                        modifier = Modifier.testTag(SmokeTestTags.SETTINGS_OPEN_QUIET_HOURS_BUTTON),
                    ) {
                        Text(text = "Открыть режим Тихие часы")
                    }
                }
            }
            composable(AppRoute.QuietHoursSchedule.route) {
                BackHandler(onBack = {
                    navController.navigateBackWithPolicy(AppRoute.QuietHoursSchedule.route)
                })
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTagExists(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTagMissing(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
}
