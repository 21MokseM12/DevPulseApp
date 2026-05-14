package com.devpulse.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLogicTest {
    @Test
    fun mergeOpenUpdatesRequest_keepsTrue_whenAlreadyRequested() {
        val merged = mergeOpenUpdatesRequest(current = true, hasOpenUpdatesExtra = false)

        assertTrue(merged)
    }

    @Test
    fun mergeOpenUpdatesRequest_setsTrue_whenNewIntentContainsExtra() {
        val merged = mergeOpenUpdatesRequest(current = false, hasOpenUpdatesExtra = true)

        assertTrue(merged)
    }

    @Test
    fun mergeOpenUpdatesRequest_keepsFalse_whenNoRequests() {
        val merged = mergeOpenUpdatesRequest(current = false, hasOpenUpdatesExtra = false)

        assertFalse(merged)
    }

    @Test
    fun mergeDigestContextRequest_returnsIncoming_whenIncomingPresent() {
        val current =
            OpenUpdatesDigestContextRequest(
                unreadOnly = true,
                periodStartEpochMs = 10L,
                periodEndEpochMs = 20L,
            )
        val incoming =
            OpenUpdatesDigestContextRequest(
                unreadOnly = true,
                periodStartEpochMs = 30L,
                periodEndEpochMs = 40L,
            )

        val merged = mergeDigestContextRequest(current = current, incoming = incoming)

        assertTrue(merged === incoming)
    }

    @Test
    fun mergeDigestContextRequest_keepsCurrent_whenIncomingMissing() {
        val current =
            OpenUpdatesDigestContextRequest(
                unreadOnly = true,
                periodStartEpochMs = 10L,
                periodEndEpochMs = 20L,
            )

        val merged = mergeDigestContextRequest(current = current, incoming = null)

        assertTrue(merged === current)
    }

    @Test
    fun digestContextRequestFromExtras_returnsRequest_whenUnreadOnlyAndWindowProvided() {
        val request =
            digestContextRequestFromExtras(
                unreadOnly = true,
                periodStartEpochMs = 100L,
                periodEndEpochMs = 200L,
            )

        assertTrue(request != null)
        assertTrue(request?.unreadOnly == true)
        assertTrue(request?.periodStartEpochMs == 100L)
        assertTrue(request?.periodEndEpochMs == 200L)
    }

    @Test
    fun digestContextRequestFromExtras_returnsNull_whenNoDigestExtras() {
        val request =
            digestContextRequestFromExtras(
                unreadOnly = false,
                periodStartEpochMs = null,
                periodEndEpochMs = null,
            )

        assertNull(request)
    }
}
