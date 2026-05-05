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
            )

        assertNull(violation)
    }

    @Test
    fun createAuthTransportViolation_returnsNull_forHttpInDebug() {
        val violation =
            createAuthTransportViolation(
                baseUrl = "http://10.0.2.2:8080/",
                environment = "debug",
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
                ),
            )

        assertEquals(ApiErrorKind.Configuration, violation.kind)
        assertEquals("Авторизация недоступна: небезопасный адрес сервера.", violation.userMessage)
        assertEquals(
            "Auth over non-HTTPS base URL is blocked for release.",
            violation.technicalDescription,
        )
    }
}
