package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.ApiErrorResponseDto
import com.devpulse.app.di.redactSensitiveLogData
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import java.io.IOException
import java.net.SocketTimeoutException

class ApiErrorMapper {
    fun mapApiError(
        statusCode: Int,
        rawError: ApiErrorResponseDto?,
    ): ApiError {
        val kind =
            when (statusCode) {
                400 -> ApiErrorKind.BadRequest
                404 -> ApiErrorKind.NotFound
                else -> ApiErrorKind.Unknown
            }
        val fallbackMessage =
            when (kind) {
                ApiErrorKind.BadRequest -> "Проверьте корректность введенных данных."
                ApiErrorKind.NotFound -> "Запрошенный ресурс не найден."
                ApiErrorKind.Configuration -> "Ошибка конфигурации приложения. Обратитесь к разработчику."
                ApiErrorKind.Unknown -> "Произошла ошибка сервера. Попробуйте позже."
                ApiErrorKind.NetworkTimeout -> "Превышено время ожидания сети. Повторите попытку."
                ApiErrorKind.Network -> "Ошибка сети. Проверьте подключение к интернету."
            }
        return ApiError(
            kind = kind,
            userMessage = rawError?.description?.takeIf { it.isNotBlank() } ?: fallbackMessage,
            statusCode = statusCode,
            code = rawError?.code,
            technicalDescription = rawError?.exceptionMessage?.let(::redactSensitiveLogData),
        )
    }

    fun mapNetworkError(throwable: Throwable): ApiError {
        val kind =
            when (throwable) {
                is SocketTimeoutException -> ApiErrorKind.NetworkTimeout
                is IOException -> ApiErrorKind.Network
                else -> ApiErrorKind.Unknown
            }
        val message =
            when (kind) {
                ApiErrorKind.Configuration -> "Ошибка конфигурации приложения. Обратитесь к разработчику."
                ApiErrorKind.NetworkTimeout -> "Превышено время ожидания сети. Повторите попытку."
                ApiErrorKind.Network -> "Ошибка сети. Проверьте подключение к интернету."
                else -> "Произошла непредвиденная ошибка. Попробуйте снова."
            }
        return ApiError(
            kind = kind,
            userMessage = message,
            technicalDescription = throwable.message?.let(::redactSensitiveLogData),
        )
    }
}
