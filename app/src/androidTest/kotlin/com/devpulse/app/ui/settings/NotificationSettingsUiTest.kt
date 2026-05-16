package com.devpulse.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
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
                onThemeModeSelected = {},
                onOpenQuietHoursSchedule = {},
                onPermissionRequestTriggered = {},
                onNotificationToggleChanged = {},
                onNotificationPresentationModeSelected = {},
                onNotificationDigestModeToggled = {},
                onNotificationDigestModeSelected = {},
                onQuietHoursEnabledChanged = {},
                onSystemNotificationCapabilityChanged = {},
                onLogoutRequested = {},
                onUnregisterRequested = {},
                onUnregisterDismissed = {},
                onUnregisterConfirmed = {},
            )
        }

        composeRule.onNodeWithText("Показывать системные уведомления").assertIsDisplayed()
        composeRule.onNodeWithText("Digest mode").assertIsDisplayed()
        composeRule.onNodeWithText("Preview уведомления").assertIsDisplayed()
        composeRule.onNodeWithText("Тихие часы").assertIsDisplayed()
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
                onThemeModeSelected = {},
                onOpenQuietHoursSchedule = {},
                onPermissionRequestTriggered = {},
                onNotificationToggleChanged = {},
                onNotificationPresentationModeSelected = {},
                onNotificationDigestModeToggled = {},
                onNotificationDigestModeSelected = {},
                onQuietHoursEnabledChanged = {},
                onSystemNotificationCapabilityChanged = {},
                onLogoutRequested = {},
                onUnregisterRequested = {},
                onUnregisterDismissed = {},
                onUnregisterConfirmed = {},
            )
        }

        composeRule.onNodeWithText("Quiet hours:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ближайший старт:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ближайшее завершение:", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_opensQuietHoursScheduleFromCard() {
        var openClicks = 0
        composeRule.setContent {
            SettingsScreen(
                uiState = SettingsUiState(notificationPreferences = NotificationPreferences()),
                onThemeModeSelected = {},
                onOpenQuietHoursSchedule = { openClicks += 1 },
                onPermissionRequestTriggered = {},
                onNotificationToggleChanged = {},
                onNotificationPresentationModeSelected = {},
                onNotificationDigestModeToggled = {},
                onNotificationDigestModeSelected = {},
                onQuietHoursEnabledChanged = {},
                onSystemNotificationCapabilityChanged = {},
                onLogoutRequested = {},
                onUnregisterRequested = {},
                onUnregisterDismissed = {},
                onUnregisterConfirmed = {},
            )
        }

        composeRule.onNodeWithText("Изменить расписание").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(1, openClicks)
        }
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

    @Test
    fun quietHoursScheduleScreen_showsDedicatedPreviewSection() {
        composeRule.setContent {
            QuietHoursScheduleScreen(
                policy =
                    QuietHoursPolicy(
                        enabled = true,
                        fromMinutes = 22 * 60,
                        toMinutes = 6 * 60,
                        weekdays = DayOfWeek.entries.toSet(),
                        timezoneMode = QuietHoursTimezoneMode.Fixed,
                        fixedZoneId = "UTC",
                    ),
                onNavigateBack = {},
                onQuietHoursEnabledChanged = {},
                onQuietHoursStartShifted = { _ -> },
                onQuietHoursEndShifted = { _ -> },
                onQuietHoursWeekdayToggled = { _ -> },
                onQuietHoursTimezoneModeSelected = { _ -> },
            )
        }

        composeRule.onNodeWithText("Временной диапазон").assertIsDisplayed()
        composeRule.onNodeWithText("Ближайшее окно").assertIsDisplayed()
        composeRule.onNodeWithText("Следующий старт:", substring = true).assertIsDisplayed()
    }
}
