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
    fun shouldQueueTokenRegistration_returnsFalse_whenLoginMissing() {
        assertFalse(shouldQueueTokenRegistration(token = "fcm-token", login = null))
    }

    @Test
    fun shouldQueueTokenRegistration_returnsFalse_whenTokenMissing() {
        assertFalse(shouldQueueTokenRegistration(token = "   ", login = "moksem"))
    }

    @Test
    fun shouldQueueTokenRegistration_returnsTrue_whenTokenAndLoginPresent() {
        assertTrue(shouldQueueTokenRegistration(token = "fcm-token", login = "moksem"))
    }

    @Test
    fun resolvePushTokenRegistrationAction_skipsWhenLoggedOut() {
        val action =
            resolvePushTokenRegistrationAction(
                token = "fcm-token",
                login = null,
                previousLogin = "moksem",
            )

        assertTrue(action is PushTokenRegistrationAction.Skip)
        assertEquals(null, action.nextPreviousLogin)
    }

    @Test
    fun resolvePushTokenRegistrationAction_requestsTokenWhenSessionExistsWithoutToken() {
        val action =
            resolvePushTokenRegistrationAction(
                token = null,
                login = "moksem",
                previousLogin = null,
            )

        assertTrue(action is PushTokenRegistrationAction.RequestToken)
        assertEquals("moksem", action.nextPreviousLogin)
    }

    @Test
    fun resolvePushTokenRegistrationAction_registersWhenSessionRestoredAfterLogout() {
        val action =
            resolvePushTokenRegistrationAction(
                token = "fcm-token",
                login = "moksem",
                previousLogin = null,
            )

        assertTrue(action is PushTokenRegistrationAction.Register)
        assertEquals("fcm-token", (action as PushTokenRegistrationAction.Register).token)
        assertEquals("session_restored", action.reason)
        assertEquals("moksem", action.nextPreviousLogin)
    }

    @Test
    fun resolvePushTokenRegistrationAction_registersWhenTokenChangesForActiveSession() {
        val action =
            resolvePushTokenRegistrationAction(
                token = "fcm-token-2",
                login = "moksem",
                previousLogin = "moksem",
            )

        assertTrue(action is PushTokenRegistrationAction.Register)
        assertEquals("session_or_token_changed", (action as PushTokenRegistrationAction.Register).reason)
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
