package com.devpulse.app.domain.validation

object AuthCredentialsValidationPolicy {
    const val MIN_LOGIN_LENGTH: Int = 4
    const val MIN_PASSWORD_LENGTH: Int = 8

    // Allowed symbols for both login and password.
    const val ALLOWED_SYMBOLS_DESCRIPTION: String = "латинские буквы, цифры и символы . _ -"

    private val allowedCharsRegex = Regex("^[a-zA-Z0-9._-]+$")
    private val letterRegex = Regex("[a-zA-Z]")
    private val digitRegex = Regex("[0-9]")

    fun hasAllowedCharacters(value: String): Boolean = value.isNotBlank() && allowedCharsRegex.matches(value)

    fun hasLetter(value: String): Boolean = letterRegex.containsMatchIn(value)

    fun hasDigit(value: String): Boolean = digitRegex.containsMatchIn(value)
}
