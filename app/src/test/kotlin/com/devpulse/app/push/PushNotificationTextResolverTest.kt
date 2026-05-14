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

    @Test
    fun resolveDigestSummaryBody_hourlyMode_returnsDigestDescription() {
        val result = resolver.resolveDigestSummaryBody(NotificationDigestMode.Hourly)

        assertEquals(PushNotificationTextResolver.HOURLY_DIGEST_SUMMARY_BODY, result)
    }

    @Test
    fun resolveDigestBody_rendersCountAndSources() {
        val result =
            resolver.resolveDigestBody(
                DigestSummaryPayload(
                    updatesCount = 3,
                    periodStartEpochMs = 10L,
                    periodEndEpochMs = 20L,
                    sourceBreakdown = mapOf("github" to 2, "jira" to 1),
                ),
            )

        assertEquals("Найдено 3 новых событий. Источники: github: 2, jira: 1.", result)
    }
}
