package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestDeliveryContractTest {
    @Test
    fun shouldDeliverInstantNotification_returnsFalse_whenDigestEnabled() {
        assertTrue(DigestDeliveryContract.shouldDeliverInstantNotification(digestMode = null))
        assertFalse(DigestDeliveryContract.shouldDeliverInstantNotification(NotificationDigestMode.Daily))
    }

    @Test
    fun normalizeSource_trimsLowercasesAndAppliesUnknownFallback() {
        assertEquals("github", DigestDeliveryContract.normalizeSource("  GitHub "))
        assertEquals(DigestDeliveryContract.UNKNOWN_SOURCE, DigestDeliveryContract.normalizeSource("   "))
    }

    @Test
    fun topSourcesForDigestBody_appliesMaxAndSortsByCountDesc() {
        val top =
            DigestDeliveryContract.topSourcesForDigestBody(
                mapOf(
                    "github" to 3,
                    "jira" to 1,
                    "bot" to 5,
                    "gitlab" to 2,
                ),
            )

        assertEquals(listOf("bot", "github", "gitlab"), top.map { it.key })
        assertEquals(DigestDeliveryContract.MAX_SOURCES_IN_DIGEST_BODY, top.size)
    }
}
