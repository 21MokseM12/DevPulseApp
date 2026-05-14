package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PushNotificationTextResolverTest {
    private val resolver = PushNotificationTextResolver()

    @Test
    fun resolveTitle_blankValue_returnsDefaultTitle() {
        val result = resolver.resolveTitle("   ")

        assertEquals(PushNotificationTextResolver.DEFAULT_NOTIFICATION_TITLE, result)
    }

    @Test
    fun resolveBody_blankValue_returnsDefaultBody() {
        val result = resolver.resolveBody("", NotificationPresentationMode.Detailed)

        assertEquals(PushNotificationTextResolver.DEFAULT_NOTIFICATION_BODY, result)
    }

    @Test
    fun resolveBody_nonBlankValue_returnsTrimmedBody() {
        val result =
            resolver.resolveBody(
                "  Есть новые изменения  ",
                NotificationPresentationMode.Detailed,
            )

        assertEquals("Есть новые изменения", result)
    }

    @Test
    fun resolveBody_compactMode_returnsCompactText() {
        val result =
            resolver.resolveBody(
                "Подробный текст должен быть скрыт",
                NotificationPresentationMode.Compact,
            )

        assertEquals(PushNotificationTextResolver.COMPACT_NOTIFICATION_BODY, result)
    }

    @Test
    fun resolvePreviewBody_digestMode_returnsDigestSummaryText() {
        val result =
            resolver.resolvePreviewBody(
                presentationMode = NotificationPresentationMode.Detailed,
                digestMode = NotificationDigestMode.Daily,
            )

        assertEquals(PushNotificationTextResolver.DAILY_DIGEST_SUMMARY_BODY, result)
    }

    @Test
    fun resolveDigestSummaryBody_dailyMode_returnsDigestDescription() {
        val result = resolver.resolveDigestSummaryBody(NotificationDigestMode.Daily)

        assertEquals(PushNotificationTextResolver.DAILY_DIGEST_SUMMARY_BODY, result)
    }
}
