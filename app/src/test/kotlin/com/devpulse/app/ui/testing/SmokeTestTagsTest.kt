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
}
