package com.devpulse.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.devpulse.app.MainActivity
import org.junit.Rule
import org.junit.Test

class DevPulseAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startScreen_showsMvpTitle() {
        composeRule.onNodeWithText("DevPulse MVP").assertIsDisplayed()
    }
}
