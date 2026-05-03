package com.devpulse.app

import org.junit.Assert.assertFalse
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
}
