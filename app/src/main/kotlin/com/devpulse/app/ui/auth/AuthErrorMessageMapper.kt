package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind

internal object AuthErrorMessageMapper {
    fun map(
        action: AuthAction,
        error: ApiError,
    ): String {
        val prefix =
            when (action) {
                AuthAction.Login -> "Не удалось войти."
                AuthAction.Register -> "Не удалось зарегистрироваться."
            }
        val detail =
            when (error.kind) {
                ApiErrorKind.BadRequest -> badRequestDetail(action, error.userMessage)
                ApiErrorKind.NotFound ->
                    when (action) {
                        AuthAction.Login -> "Аккаунт с таким логином не найден."
                        AuthAction.Register -> "Сервис авторизации временно недоступен. Повторите попытку позже."
                    }
                ApiErrorKind.Configuration ->
                    "Ошибка конфигурации приложения. Обновите приложение или обратитесь в поддержку."
                ApiErrorKind.NetworkTimeout ->
                    "Превышено время ожидания ответа сервера. Проверьте сеть и попробуйте снова."
                ApiErrorKind.Network -> "Нет соединения с сервером. Проверьте интернет и попробуйте снова."
                ApiErrorKind.Unknown -> unknownDetail(error.userMessage)
            }
        return "$prefix $detail"
    }

    private fun badRequestDetail(
        action: AuthAction,
        rawMessage: String,
    ): String {
        val normalizedMessage = rawMessage.trim()
        if (normalizedMessage.isBlank()) {
            return defaultBadRequestDetail(action)
        }
        if (normalizedMessage.equals("Проверьте корректность введенных данных.", ignoreCase = true)) {
            return defaultBadRequestDetail(action)
        }
        if (isGenericServerError(normalizedMessage)) {
            return defaultBadRequestDetail(action)
        }
        return "$normalizedMessage Проверьте введенные данные и повторите попытку."
    }

    private fun defaultBadRequestDetail(action: AuthAction): String {
        return when (action) {
            AuthAction.Login -> "Проверьте логин и пароль и повторите попытку."
            AuthAction.Register ->
                "Аккаунт с этим логином уже существует, измените логин и повторите попытку."
        }
    }

    private fun isGenericServerError(message: String): Boolean {
        val lower = message.lowercase()
        return lower == "bad request" || lower == "bad_request"
    }

    private fun unknownDetail(rawMessage: String): String {
        val normalizedMessage = rawMessage.trim()
        return if (normalizedMessage.isBlank()) {
            "Сервис временно недоступен. Повторите попытку позже."
        } else {
            "$normalizedMessage Повторите попытку позже."
        }
    }
}
