package com.devpulse.app.push

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushInitializerLogicTest {
    @Test
    fun shouldRequestFcmToken_returnsFalse_whenFirebaseNotConfigured() {
        assertFalse(shouldRequestFcmToken(firebaseConfigured = false))
    }

    @Test
    fun shouldRequestFcmToken_returnsTrue_whenFirebaseConfigured() {
        assertTrue(shouldRequestFcmToken(firebaseConfigured = true))
    }

    @Test
    fun shouldSaveToken_returnsTrue_whenTaskSuccessfulAndTokenPresent() {
        assertTrue(shouldSaveToken(taskSuccessful = true, token = "fcm-token"))
    }

    @Test
    fun shouldSaveToken_returnsFalse_whenTaskFailed() {
        assertFalse(shouldSaveToken(taskSuccessful = false, token = "fcm-token"))
    }

    @Test
    fun shouldSaveToken_returnsFalse_whenTokenBlank() {
        assertFalse(shouldSaveToken(taskSuccessful = true, token = "   "))
    }

    @Test
    fun updatesNotificationChannelConfig_returnsStableUpdatesChannelContract() {
        val channelConfig = updatesNotificationChannelConfig()

        assertEquals(PushNotificationChannels.UPDATES_CHANNEL_ID, channelConfig.id)
        assertEquals(PushNotificationChannels.UPDATES_CHANNEL_NAME, channelConfig.name)
        assertEquals(PushNotificationChannels.UPDATES_CHANNEL_DESCRIPTION, channelConfig.description)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channelConfig.importance)
    }
}
