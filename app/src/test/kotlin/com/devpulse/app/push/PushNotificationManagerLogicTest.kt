package com.devpulse.app.push

import android.os.Build
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationManagerLogicTest {
    @Test
    fun notificationIdForUpdate_usesRemoteEventId_whenAvailable() {
        val first =
            ParsedPushUpdate(
                remoteEventId = "evt-1",
                linkUpdateId = null,
                updateOwner = "unknown",
                creationDate = "",
                linkUrl = "https://example.com/a",
                title = "title",
                content = "body",
            )
        val second =
            ParsedPushUpdate(
                remoteEventId = "evt-1",
                linkUpdateId = null,
                updateOwner = "unknown",
                creationDate = "",
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

    @Test
    fun shouldPostIndividualNotification_returnsFalse_whenDigestEnabled() {
        assertTrue(shouldPostIndividualNotification(null))
        assertFalse(shouldPostIndividualNotification(NotificationDigestMode.Daily))
    }

    @Test
    fun digestWindowOrNull_returnsPair_whenBothBoundsPresent() {
        val window =
            digestWindowOrNull(
                periodStartEpochMs = 100L,
                periodEndEpochMs = 200L,
            )

        assertEquals(100L to 200L, window)
    }

    @Test
    fun digestWindowOrNull_returnsNull_whenWindowIncomplete() {
        val window =
            digestWindowOrNull(
                periodStartEpochMs = 100L,
                periodEndEpochMs = null,
            )

        assertNull(window)
    }
}
