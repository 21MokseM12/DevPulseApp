package com.devpulse.app.data.repository

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionsErrorMapperTest {
    @Test
    fun shouldTreatAsEmpty_returnsTrueForKnownEmptyCode() {
        val error =
            ApiError(
                kind = ApiErrorKind.BadRequest,
                userMessage = "No subscriptions found for this client",
                statusCode = 400,
                code = "EMPTY_SUBSCRIPTIONS",
            )

        assertTrue(SubscriptionsErrorMapper.shouldTreatAsEmpty(error))
    }

    @Test
    fun shouldTreatAsEmpty_returnsTrueForKnownMessageHint() {
        val error =
            ApiError(
                kind = ApiErrorKind.BadRequest,
                userMessage = "No subscriptions yet for this client",
                statusCode = 400,
                code = null,
            )

        assertTrue(SubscriptionsErrorMapper.shouldTreatAsEmpty(error))
    }

    @Test
    fun shouldTreatAsEmpty_returnsFalseForGenericBadRequest() {
        val error =
            ApiError(
                kind = ApiErrorKind.BadRequest,
                userMessage = "Проверьте корректность введенных данных.",
                statusCode = 400,
                code = null,
            )

        assertFalse(SubscriptionsErrorMapper.shouldTreatAsEmpty(error))
    }
}
