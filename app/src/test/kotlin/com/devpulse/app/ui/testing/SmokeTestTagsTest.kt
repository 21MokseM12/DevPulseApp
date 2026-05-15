package com.devpulse.app.ui.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTestTagsTest {
    @Test
    fun subscriptionRow_generatesStableTag() {
        assertEquals("subscriptions_row_42", SmokeTestTags.subscriptionRow(42))
    }

    @Test
    fun subscriptionRemoveButton_generatesStableTag() {
        assertEquals("subscriptions_remove_button_7", SmokeTestTags.subscriptionRemoveButton(7))
    }

    @Test
    fun updateMarkReadButton_generatesStableTag() {
        assertEquals("updates_mark_read_button_9", SmokeTestTags.updateMarkReadButton(9))
    }

    @Test
    fun subscriptionTagFilter_generatesStableTag() {
        assertEquals("subscriptions_tag_filter_android", SmokeTestTags.subscriptionTagFilter("Android"))
    }

    @Test
    fun subscriptionsEmptyPrimaryButton_hasStableTag() {
        assertEquals("subscriptions_empty_primary_button", SmokeTestTags.SUBSCRIPTIONS_EMPTY_PRIMARY_BUTTON)
    }

    @Test
    fun navigationBackButton_hasStableTag() {
        assertEquals("navigation_back_button", SmokeTestTags.NAVIGATION_BACK_BUTTON)
    }

    @Test
    fun navigationTopBarTitle_hasStableTag() {
        assertEquals("navigation_top_bar_title", SmokeTestTags.NAVIGATION_TOP_BAR_TITLE)
    }

    @Test
    fun subscriptionsOpenSettingsButton_hasStableTag() {
        assertEquals("subscriptions_open_settings_button", SmokeTestTags.SUBSCRIPTIONS_OPEN_SETTINGS_BUTTON)
    }

    @Test
    fun settingsOpenQuietHoursButton_hasStableTag() {
        assertEquals("settings_open_quiet_hours_button", SmokeTestTags.SETTINGS_OPEN_QUIET_HOURS_BUTTON)
    }

    @Test
    fun settingsNotificationsSwitch_hasStableTag() {
        assertEquals("settings_notifications_switch", SmokeTestTags.SETTINGS_NOTIFICATIONS_SWITCH)
    }
}
