package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMessageMapperTest {
    @Test
    fun map_loginBadRequest_usesActionableHintWhenMessageIsGeneric() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Login,
                error =
                    ApiError(
                        kind = ApiErrorKind.BadRequest,
                        userMessage = "Проверьте корректность введенных данных.",
                    ),
            )

        assertEquals("Не удалось войти. Проверьте логин и пароль и повторите попытку.", message)
    }

    @Test
    fun map_registerBadRequest_withBlankMessage_usesRegisterFallbackHint() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Register,
                error =
                    ApiError(
                        kind = ApiErrorKind.BadRequest,
                        userMessage = "   ",
                    ),
            )

        assertEquals(
            "Не удалось зарегистрироваться. Проверьте данные регистрации или попробуйте войти в существующий аккаунт.",
            message,
        )
    }

    @Test
    fun map_registerBadRequest_keepsBackendReasonAndAddsActionHint() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Register,
                error =
                    ApiError(
                        kind = ApiErrorKind.BadRequest,
                        userMessage = "Пользователь уже существует",
                    ),
            )

        assertEquals(
            "Не удалось зарегистрироваться. Пользователь уже существует " +
                "Проверьте введенные данные и повторите попытку.",
            message,
        )
    }

    @Test
    fun map_notFound_returnsAuthServiceUnavailableHint() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Login,
                error =
                    ApiError(
                        kind = ApiErrorKind.NotFound,
                        userMessage = "Endpoint not found",
                    ),
            )

        assertEquals(
            "Не удалось войти. Сервис авторизации временно недоступен. Повторите попытку позже.",
            message,
        )
    }

    @Test
    fun map_configuration_returnsAppConfigurationGuidance() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Register,
                error =
                    ApiError(
                        kind = ApiErrorKind.Configuration,
                        userMessage = "TLS pin mismatch",
                    ),
            )

        assertEquals(
            "Не удалось зарегистрироваться. Ошибка конфигурации приложения. " +
                "Обновите приложение или обратитесь в поддержку.",
            message,
        )
    }

    @Test
    fun map_networkTimeout_returnsRetryGuidance() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Login,
                error =
                    ApiError(
                        kind = ApiErrorKind.NetworkTimeout,
                        userMessage = "Превышено время ожидания сети",
                    ),
            )

        assertEquals(
            "Не удалось войти. Превышено время ожидания ответа сервера. Проверьте сеть и попробуйте снова.",
            message,
        )
    }

    @Test
    fun map_network_returnsConnectivityGuidance() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Login,
                error =
                    ApiError(
                        kind = ApiErrorKind.Network,
                        userMessage = "No route to host",
                    ),
            )

        assertEquals(
            "Не удалось войти. Нет соединения с сервером. Проверьте интернет и попробуйте снова.",
            message,
        )
    }

    @Test
    fun map_unknownWithMessage_keepsBackendTextAndAddsRetryHint() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Login,
                error =
                    ApiError(
                        kind = ApiErrorKind.Unknown,
                        userMessage = "Сервис перегружен",
                    ),
            )

        assertEquals(
            "Не удалось войти. Сервис перегружен Повторите попытку позже.",
            message,
        )
    }

    @Test
    fun map_unknownWithEmptyMessage_returnsStableFallback() {
        val message =
            AuthErrorMessageMapper.map(
                action = AuthAction.Register,
                error =
                    ApiError(
                        kind = ApiErrorKind.Unknown,
                        userMessage = " ",
                    ),
            )

        assertEquals(
            "Не удалось зарегистрироваться. Сервис временно недоступен. Повторите попытку позже.",
            message,
        )
    }
}
