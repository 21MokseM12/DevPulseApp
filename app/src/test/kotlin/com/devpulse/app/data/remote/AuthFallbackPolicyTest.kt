package com.devpulse.app.data.remote

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFallbackPolicyTest {
    @Test
    fun shouldFallbackToRegister_returnsTrueFor404() {
        val fallback =
            AuthFallbackPolicy.shouldFallbackToRegister(
                statusCode = 404,
                error = apiError(ApiErrorKind.NotFound, "not found"),
            )

        assertTrue(fallback)
    }

    @Test
    fun shouldFallbackToRegister_returnsTrueForBadRequestWithEndpointMarker() {
        val fallback =
            AuthFallbackPolicy.shouldFallbackToRegister(
                statusCode = 400,
                error = apiError(ApiErrorKind.BadRequest, "No static resource /api/v1/clients/login"),
            )

        assertTrue(fallback)
    }

    @Test
    fun shouldFallbackToRegister_returnsFalseForGenericBadRequest() {
        val fallback =
            AuthFallbackPolicy.shouldFallbackToRegister(
                statusCode = 400,
                error = apiError(ApiErrorKind.BadRequest, "Неверный пароль"),
            )

        assertFalse(fallback)
    }

    @Test
    fun shouldTreatRegisterConflictAsLoginSuccess_detectsAlreadyExistsSignals() {
        val shouldTreatAsSuccess =
            AuthFallbackPolicy.shouldTreatRegisterConflictAsLoginSuccess(
                apiError(
                    kind = ApiErrorKind.BadRequest,
                    message = "Client already exists",
                    code = "already_exists",
                ),
            )

        assertTrue(shouldTreatAsSuccess)
    }

    @Test
    fun shouldTreatRegisterConflictAsLoginSuccess_returnsFalseForNotFound() {
        val shouldTreatAsSuccess =
            AuthFallbackPolicy.shouldTreatRegisterConflictAsLoginSuccess(
                apiError(
                    kind = ApiErrorKind.NotFound,
                    message = "Client not found",
                    code = "not_found",
                ),
            )

        assertFalse(shouldTreatAsSuccess)
    }

    @Test
    fun shouldTreatRegisterConflictAsLoginSuccess_returnsFalseForUnknownConflictMessage() {
        val shouldTreatAsSuccess =
            AuthFallbackPolicy.shouldTreatRegisterConflictAsLoginSuccess(
                apiError(
                    kind = ApiErrorKind.BadRequest,
                    message = "Client cannot be created in current state",
                    code = "register_rejected",
                ),
            )

        assertFalse(shouldTreatAsSuccess)
    }

    private fun apiError(
        kind: ApiErrorKind,
        message: String,
        code: String? = null,
        technicalDescription: String? = null,
    ): ApiError {
        return ApiError(
            kind = kind,
            userMessage = message,
            code = code,
            technicalDescription = technicalDescription,
        )
    }
}
