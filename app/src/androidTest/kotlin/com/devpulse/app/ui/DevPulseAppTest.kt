package com.devpulse.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devpulse.app.MainActivity
import org.junit.Rule
import org.junit.Test

class DevPulseAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStart_opensAuthRoute() {
        composeRule.waitUntilNodeWithTextExists("Auth")
        composeRule.onNodeWithText("Auth").assertIsDisplayed()
    }

    @Test
    fun loginAndLogout_navigatesWithoutDuplicateScreens() {
        composeRule.waitUntilNodeWithTextExists("Войти")
        composeRule.onNodeWithText("Войти").performClick()
        composeRule.waitUntilNodeWithTextExists("Subscriptions")
        composeRule.onNodeWithText("Subscriptions").assertIsDisplayed()

        composeRule.onNodeWithText("Выйти").performClick()
        composeRule.waitUntilNodeWithTextExists("Auth")
        composeRule.onNodeWithText("Auth").assertIsDisplayed()
    }
}

private fun ComposeContentTestRule.waitUntilNodeWithTextExists(text: String) {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
