package com.devpulse.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportSecurityConfigTest {
    @Test
    fun resolveTransportPinsForEnvironment_returnsReleasePins_forReleaseEnvironment() {
        val pins =
            resolveTransportPinsForEnvironment(
                environment = "release",
                releasePinsConfig = "sha256/releaseA=, sha256/releaseB=",
                stagingPinsConfig = "sha256/stagingA=",
            )

        assertEquals(setOf("sha256/releaseA=", "sha256/releaseB="), pins)
    }

    @Test
    fun resolveTransportPinsForEnvironment_returnsStagingPins_forStagingEnvironment() {
        val pins =
            resolveTransportPinsForEnvironment(
                environment = "staging",
                releasePinsConfig = "sha256/releaseA=",
                stagingPinsConfig = "sha256/stagingA=,sha256/stagingB=",
            )

        assertEquals(setOf("sha256/stagingA=", "sha256/stagingB="), pins)
    }

    @Test
    fun resolveTransportPinsForEnvironment_filtersMalformedPins() {
        val pins =
            resolveTransportPinsForEnvironment(
                environment = "staging",
                releasePinsConfig = "",
                stagingPinsConfig = "sha1/legacy,not-a-pin,sha256/",
            )

        assertEquals(emptySet<String>(), pins)
    }
}
