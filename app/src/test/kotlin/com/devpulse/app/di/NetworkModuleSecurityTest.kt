package com.devpulse.app.di

import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleSecurityTest {
    @Test
    fun sensitiveHeadersForRedaction_containsSecurityHeaders() {
        val headers = sensitiveHeadersForRedaction()

        assertTrue("Authorization" in headers)
        assertTrue("Cookie" in headers)
        assertTrue("Set-Cookie" in headers)
        assertTrue("Client-Login" in headers)
    }

    @Test
    fun createLoggingInterceptor_appliesProvidedLevel() {
        val interceptor = createLoggingInterceptor(HttpLoggingInterceptor.Level.BASIC)

        assertEquals(HttpLoggingInterceptor.Level.BASIC, interceptor.level)
    }

    @Test
    fun createCertificatePinner_returnsNull_forDebugEnvironment() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://api.devpulse.example/",
                environment = "debug",
                releasePinsConfig = "",
                stagingPinsConfig = "",
            )

        assertNull(pinner)
    }

    @Test
    fun createCertificatePinner_returnsConfiguredPinner_forReleasePinnedHost() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://api.devpulse.example/",
                environment = "release",
                releasePinsConfig =
                    "sha256/afwiKY3RxoMmL1+gD2Q2T6f1V3l0Y7S4A5kZZgwyUrw=," +
                        "sha256/klO23n5h5pLxL7f3vR7Fj1hX1WfNfHwO51j5jC9f4QY=",
                stagingPinsConfig = "",
            )

        assertNotNull(pinner)
    }

    @Test
    fun createCertificatePinner_ignoresMalformedPins() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://api.devpulse.example/",
                environment = "release",
                releasePinsConfig =
                    "not-a-pin,sha1/legacy,sha256/afwiKY3RxoMmL1+gD2Q2T6f1V3l0Y7S4A5kZZgwyUrw=",
                stagingPinsConfig = "",
            )

        assertNotNull(pinner)
    }

    @Test
    fun createCertificatePinner_returnsNull_forMissingPins() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://example.org/",
                environment = "release",
                releasePinsConfig = "",
                stagingPinsConfig = "",
            )

        assertNull(pinner)
    }

    @Test
    fun redactSensitiveLogData_masksQueryJsonAndBearerToken() {
        val raw =
            "POST /api/v1/clients?password=super-secret&token=abc123 Authorization: Bearer top-secret " +
                "{\"password\":\"admin123\",\"accessToken\":\"xyz\"}"

        val redacted = redactSensitiveLogData(raw)

        assertTrue(redacted.contains("password=***"))
        assertTrue(redacted.contains("token=***"))
        assertTrue(redacted.contains("Bearer ***"))
        assertTrue(redacted.contains(""""password":"***""""))
        assertTrue(redacted.contains(""""accessToken":"***""""))
    }

    @Test(expected = IllegalStateException::class)
    fun enforceProductionPinningPolicy_throws_forHttpsReleaseWithoutPins() {
        enforceProductionPinningPolicy(
            baseUrl = "https://api.devpulse.example/",
            environment = "release",
            hasCertificatePinner = false,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun enforceProductionPinningPolicy_throws_forHttpRelease() {
        enforceProductionPinningPolicy(
            baseUrl = "http://api.devpulse.example/",
            environment = "release",
            hasCertificatePinner = true,
        )
    }

    @Test
    fun enforceProductionPinningPolicy_allowsDebugWithoutPins() {
        enforceProductionPinningPolicy(
            baseUrl = "http://10.0.2.2:8080/",
            environment = "debug",
            hasCertificatePinner = false,
        )
    }
}
