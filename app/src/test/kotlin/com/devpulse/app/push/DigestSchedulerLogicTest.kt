package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DigestSchedulerLogicTest {
    @Test
    fun digestRepeatMinutes_returnsConfiguredInterval() {
        assertEquals(60L, digestRepeatMinutes(NotificationDigestMode.Hourly))
        assertEquals(360L, digestRepeatMinutes(NotificationDigestMode.EverySixHours))
        assertEquals(1440L, digestRepeatMinutes(NotificationDigestMode.Daily))
    }

    @Test
    fun digestFlexMinutes_keepsAtLeastFifteenMinutes() {
        assertEquals(15L, digestFlexMinutes(60L))
        assertEquals(90L, digestFlexMinutes(360L))
    }
}
