package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind

internal data class BackendAuthValidationMessage(
    val field: AuthValidationField,
    val message: String,
)

internal enum class AuthValidationField {
    Login,
    Password,
}

internal object BackendAuthValidationMessageMapper {
    private val loginMarkers = listOf("login", "логин", "username")
    private val passwordMarkers = listOf("password", "парол", "passcode")

    fun map(error: ApiError): BackendAuthValidationMessage? {
        if (error.kind != ApiErrorKind.BadRequest) return null

        val rawMessage = error.userMessage.trim()
        if (rawMessage.isBlank()) return null
        val normalized = rawMessage.lowercase()

        return when {
            loginMarkers.any { marker -> normalized.contains(marker) } ->
                BackendAuthValidationMessage(
                    field = AuthValidationField.Login,
                    message = "Проверьте логин. $rawMessage",
                )

            passwordMarkers.any { marker -> normalized.contains(marker) } ->
                BackendAuthValidationMessage(
                    field = AuthValidationField.Password,
                    message = "Проверьте пароль. $rawMessage",
                )

            else -> null
        }
    }
}
