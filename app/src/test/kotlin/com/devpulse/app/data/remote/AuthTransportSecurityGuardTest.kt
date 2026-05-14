package com.devpulse.app.data.remote

import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthTransportSecurityGuardTest {
    @Test
    fun createAuthTransportViolation_returnsNull_forHttpsInRelease() {
        val violation =
            createAuthTransportViolation(
                baseUrl = "https://api.devpulse.example/",
                environment = "release",
                releasePinsConfig = "sha256/releasePinA=,sha256/releasePinB=",
                stagingPinsConfig = "sha256/stagingPinA=,sha256/stagingPinB=",
            )

        assertNull(violation)
    }

    @Test
    fun createAuthTransportViolation_returnsNull_forHttpInDebug() {
        val violation =
            createAuthTransportViolation(
                baseUrl = "http://10.0.2.2:8080/",
                environment = "debug",
                releasePinsConfig = "",
                stagingPinsConfig = "",
            )

        assertNull(violation)
    }

    @Test
    fun createAuthTransportViolation_returnsConfigurationError_forHttpInRelease() {
        val violation =
            requireNotNull(
                createAuthTransportViolation(
                    baseUrl = "http://api.devpulse.example/",
                    environment = "release",
                    releasePinsConfig = "sha256/releasePinA=,sha256/releasePinB=",
                    stagingPinsConfig = "sha256/stagingPinA=,sha256/stagingPinB=",
                ),
            )

        assertEquals(ApiErrorKind.Configuration, violation.kind)
        assertEquals("Авторизация недоступна: небезопасный адрес сервера.", violation.userMessage)
        assertEquals(
            "Auth over non-HTTPS base URL is blocked for release.",
            violation.technicalDescription,
        )
    }

    @Test
    fun createAuthTransportViolation_returnsConfigurationError_whenPinsAreMissingInRelease() {
        val violation =
            requireNotNull(
                createAuthTransportViolation(
                    baseUrl = "https://api.devpulse.example/",
                    environment = "release",
                    releasePinsConfig = "",
                    stagingPinsConfig = "sha256/stagingPinA=,sha256/stagingPinB=",
                ),
            )

        assertEquals(ApiErrorKind.Configuration, violation.kind)
        assertEquals("Авторизация недоступна: не настроены TLS pin-ы.", violation.userMessage)
        assertEquals(
            "Auth is blocked for release: no TLS pins configured.",
            violation.technicalDescription,
        )
    }
}
