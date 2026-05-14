package com.devpulse.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import com.devpulse.app.push.PushNotificationTextResolver
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

class NotificationSettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsScreen_showsNotificationSectionWithPreviewAndDigestControl() {
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(notificationPreferences = NotificationPreferences()),
                onGoToSubscriptions = {},
                onGoToUpdates = {},
                onPermissionRequestTriggered = {},
                onNotificationToggleChanged = {},
                onNotificationPresentationModeSelected = {},
                onNotificationDigestModeToggled = {},
                onQuietHoursEnabledChanged = {},
                onQuietHoursStartShifted = { _ -> },
                onQuietHoursEndShifted = { _ -> },
                onQuietHoursWeekdayToggled = { _ -> },
                onQuietHoursTimezoneModeSelected = { _ -> },
                onSystemNotificationCapabilityChanged = {},
                onLogoutRequested = {},
                onUnregisterRequested = {},
                onUnregisterDismissed = {},
                onUnregisterConfirmed = {},
            )
        }

        composeRule.onNodeWithText("Показывать системные уведомления").assertIsDisplayed()
        composeRule.onNodeWithText("Digest mode (daily)").assertIsDisplayed()
        composeRule.onNodeWithText("Preview уведомления").assertIsDisplayed()
        composeRule.onNodeWithText("Quiet hours").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsQuietHoursPreviewWhenEnabled() {
        composeRule.setContent {
            SettingsScreen(
                uiState =
                    SettingsUiState(
                        notificationPreferences =
                            NotificationPreferences(
                                quietHoursPolicy =
                                    com.devpulse.app.data.local.preferences.QuietHoursPolicy(
                                        enabled = true,
                                        fromMinutes = 22 * 60,
                                        toMinutes = 7 * 60,
                                        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                                        timezoneMode = QuietHoursTimezoneMode.Fixed,
                                        fixedZoneId = "UTC",
                                    ),
                            ),
                    ),
                onGoToSubscriptions = {},
                onGoToUpdates = {},
                onPermissionRequestTriggered = {},
                onNotificationToggleChanged = {},
                onNotificationPresentationModeSelected = {},
                onNotificationDigestModeToggled = {},
                onQuietHoursEnabledChanged = {},
                onQuietHoursStartShifted = { _ -> },
                onQuietHoursEndShifted = { _ -> },
                onQuietHoursWeekdayToggled = { _ -> },
                onQuietHoursTimezoneModeSelected = { _ -> },
                onSystemNotificationCapabilityChanged = {},
                onLogoutRequested = {},
                onUnregisterRequested = {},
                onUnregisterDismissed = {},
                onUnregisterConfirmed = {},
            )
        }

        composeRule.onNodeWithText("Следующее тихое окно: 22:00–07:00 (UTC).", substring = true).assertIsDisplayed()
    }

    @Test
    fun previewCard_showsDigestPreviewWhenDigestEnabled() {
        composeRule.setContent {
            NotificationPreviewCard(
                presentationMode = NotificationPresentationMode.Detailed,
                digestMode = NotificationDigestMode.Daily,
                textResolver = PushNotificationTextResolver(),
                notificationsEnabled = true,
            )
        }

        composeRule
            .onNodeWithText(PushNotificationTextResolver.DAILY_DIGEST_SUMMARY_BODY)
            .assertIsDisplayed()
    }
}
