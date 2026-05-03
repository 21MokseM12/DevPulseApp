package com.devpulse.app.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushInitializerLogicTest {
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
}
