package com.devpulse.app.ui.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateLinkNavigationTest {
    @Test
    fun resolveNavigableUpdateLink_returnsNormalizedUrlForHttps() {
        val result = resolveNavigableUpdateLink("  https://devpulse.app/item/42  ")

        assertEquals("https://devpulse.app/item/42", result)
    }

    @Test
    fun resolveNavigableUpdateLink_returnsNullForNonHttpScheme() {
        val result = resolveNavigableUpdateLink("ftp://devpulse.app/item/42")

        assertNull(result)
    }

    @Test
    fun resolveNavigableUpdateLink_addsHttpsSchemeWhenMissing() {
        val result = resolveNavigableUpdateLink("devpulse.app/item/42")

        assertEquals("https://devpulse.app/item/42", result)
    }

    @Test
    fun resolveNavigableUpdateLink_returnsNullForMalformedUrl() {
        val result = resolveNavigableUpdateLink("not a valid url")

        assertNull(result)
    }
}
