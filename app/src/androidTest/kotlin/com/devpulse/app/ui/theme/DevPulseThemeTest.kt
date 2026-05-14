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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

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

    @Test
    fun devPulseTheme_lightScheme_meetsReadableContrastForPrimaryText() {
        var contrastRatio = 0.0

        composeRule.setContent {
            DevPulseTheme(darkTheme = false) {
                val scheme = MaterialTheme.colorScheme
                contrastRatio = calculateContrastRatio(scheme.onBackground, scheme.background)
            }
        }

        composeRule.runOnIdle {
            assertTrue("Expected contrast >= 4.5, got $contrastRatio", contrastRatio >= 4.5)
        }
    }

    @Test
    fun devPulseTheme_darkScheme_meetsReadableContrastForPrimaryText() {
        var contrastRatio = 0.0

        composeRule.setContent {
            DevPulseTheme(darkTheme = true) {
                val scheme = MaterialTheme.colorScheme
                contrastRatio = calculateContrastRatio(scheme.onBackground, scheme.background)
            }
        }

        composeRule.runOnIdle {
            assertTrue("Expected contrast >= 4.5, got $contrastRatio", contrastRatio >= 4.5)
        }
    }

    private fun calculateContrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val fgLuminance = relativeLuminance(foreground)
        val bgLuminance = relativeLuminance(background)
        val lighter = max(fgLuminance, bgLuminance)
        val darker = min(fgLuminance, bgLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(v: Float): Double {
            val normalized = v.toDouble()
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }

        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
