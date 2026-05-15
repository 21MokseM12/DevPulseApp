package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendAuthValidationMessageMapperTest {
    @Test
    fun map_badRequestWithLoginHint_returnsLoginInlineMessage() {
        val result =
            BackendAuthValidationMessageMapper.map(
                ApiError(
                    kind = ApiErrorKind.BadRequest,
                    userMessage = "login format is invalid",
                ),
            )

        assertEquals(AuthValidationField.Login, result?.field)
        assertEquals("Проверьте логин. login format is invalid", result?.message)
    }

    @Test
    fun map_badRequestWithPasswordHint_returnsPasswordInlineMessage() {
        val result =
            BackendAuthValidationMessageMapper.map(
                ApiError(
                    kind = ApiErrorKind.BadRequest,
                    userMessage = "Пароль слишком простой",
                ),
            )

        assertEquals(AuthValidationField.Password, result?.field)
        assertEquals("Проверьте пароль. Пароль слишком простой", result?.message)
    }

    @Test
    fun map_nonValidationError_returnsNull() {
        val result =
            BackendAuthValidationMessageMapper.map(
                ApiError(
                    kind = ApiErrorKind.Network,
                    userMessage = "No route to host",
                ),
            )

        assertNull(result)
    }
}
