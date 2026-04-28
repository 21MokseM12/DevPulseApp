package com.devpulse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DevPulseThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun devPulseTheme_usesLightScheme_whenDarkThemeIsFalse() {
        var background by mutableStateOf(Color.Unspecified)

        composeRule.setContent {
            DevPulseTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.background
            }
        }

        composeRule.runOnIdle {
            assertEquals(lightColorScheme().background, background)
        }
    }

    @Test
    fun devPulseTheme_usesDarkScheme_whenDarkThemeIsTrue() {
        var background by mutableStateOf(Color.Unspecified)

        composeRule.setContent {
            DevPulseTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
            }
        }

        composeRule.runOnIdle {
            assertEquals(darkColorScheme().background, background)
        }
    }
}
