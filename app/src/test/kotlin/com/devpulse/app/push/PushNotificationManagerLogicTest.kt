package com.devpulse.app.push

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationManagerLogicTest {
    @Test
    fun notificationIdForUpdate_usesRemoteEventId_whenAvailable() {
        val first =
            ParsedPushUpdate(
                remoteEventId = "evt-1",
                linkUrl = "https://example.com/a",
                title = "title",
                content = "body",
            )
        val second =
            ParsedPushUpdate(
                remoteEventId = "evt-1",
                linkUrl = "https://example.com/b",
                title = "other",
                content = "text",
            )

        assertEquals(notificationIdForUpdate(first), notificationIdForUpdate(second))
    }

    @Test
    fun canPostNotifications_returnsFalse_whenNotificationsDisabled() {
        val canPost =
            canPostNotifications(
                areNotificationsEnabled = false,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = true,
            )

        assertFalse(canPost)
    }

    @Test
    fun canPostNotifications_returnsTrue_belowTiramisu_whenNotificationsEnabled() {
        val canPost =
            canPostNotifications(
                areNotificationsEnabled = true,
                sdkInt = Build.VERSION_CODES.S,
                permissionGranted = false,
            )

        assertTrue(canPost)
    }

    @Test
    fun canPostNotifications_requiresPermission_onTiramisuAndAbove() {
        val denied =
            canPostNotifications(
                areNotificationsEnabled = true,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = false,
            )
        val granted =
            canPostNotifications(
                areNotificationsEnabled = true,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = true,
            )

        assertFalse(denied)
        assertTrue(granted)
    }
}
