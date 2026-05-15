package com.devpulse.app.domain.validation

data class AuthCredentialsValidationResult(
    val loginError: AuthCredentialsValidationError? = null,
    val passwordError: AuthCredentialsValidationError? = null,
) {
    val isValid: Boolean
        get() = loginError == null && passwordError == null
}

enum class AuthCredentialsValidationErrorType {
    Required,
    TooShort,
    InvalidCharacters,
    MissingLetter,
    MissingDigit,
}

data class AuthCredentialsValidationError(
    val type: AuthCredentialsValidationErrorType,
    val message: String,
)

class AuthCredentialsValidator {
    fun validate(
        loginRaw: String,
        passwordRaw: String,
    ): AuthCredentialsValidationResult {
        val login = loginRaw.trim()
        val password = passwordRaw.trim()
        return AuthCredentialsValidationResult(
            loginError = validateLogin(login),
            passwordError = validatePassword(password),
        )
    }

    private fun validateLogin(login: String): AuthCredentialsValidationError? {
        if (login.isBlank()) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.Required,
                message = "Введите логин.",
            )
        }
        if (login.length < AuthCredentialsValidationPolicy.MIN_LOGIN_LENGTH) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.TooShort,
                message = "Логин должен содержать минимум 4 символа.",
            )
        }
        if (!AuthCredentialsValidationPolicy.hasAllowedCharacters(login)) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.InvalidCharacters,
                message =
                    "Логин может содержать только " +
                        "${AuthCredentialsValidationPolicy.ALLOWED_SYMBOLS_DESCRIPTION}.",
            )
        }
        return null
    }

    private fun validatePassword(password: String): AuthCredentialsValidationError? {
        if (password.isBlank()) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.Required,
                message = "Введите пароль.",
            )
        }
        if (password.length < AuthCredentialsValidationPolicy.MIN_PASSWORD_LENGTH) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.TooShort,
                message = "Пароль должен содержать минимум 8 символов.",
            )
        }
        if (!AuthCredentialsValidationPolicy.hasAllowedCharacters(password)) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.InvalidCharacters,
                message =
                    "Пароль может содержать только " +
                        "${AuthCredentialsValidationPolicy.ALLOWED_SYMBOLS_DESCRIPTION}.",
            )
        }
        if (!AuthCredentialsValidationPolicy.hasLetter(password)) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.MissingLetter,
                message = "Пароль должен содержать хотя бы одну букву.",
            )
        }
        if (!AuthCredentialsValidationPolicy.hasDigit(password)) {
            return AuthCredentialsValidationError(
                type = AuthCredentialsValidationErrorType.MissingDigit,
                message = "Пароль должен содержать хотя бы одну цифру.",
            )
        }
        return null
    }
}
