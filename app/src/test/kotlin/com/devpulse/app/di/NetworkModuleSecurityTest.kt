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
            )

        assertNull(pinner)
    }

    @Test
    fun createCertificatePinner_returnsConfiguredPinner_forReleasePinnedHost() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://api.devpulse.example/",
                environment = "release",
            )

        assertNotNull(pinner)
    }

    @Test
    fun createCertificatePinner_returnsNull_forUnknownHost() {
        val pinner =
            createCertificatePinner(
                baseUrl = "https://example.org/",
                environment = "release",
            )

        assertNull(pinner)
    }
}
