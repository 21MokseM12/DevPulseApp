package com.devpulse.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCredentialsValidatorTest {
    private val validator = AuthCredentialsValidator()

    @Test
    fun validate_validCredentials_returnsNoErrors() {
        val result = validator.validate(loginRaw = "moksem_1", passwordRaw = "pass1234")

        assertTrue(result.isValid)
        assertEquals(null, result.loginError)
        assertEquals(null, result.passwordError)
    }

    @Test
    fun validate_trimmedCredentials_applyValidationRulesToTrimmedValues() {
        val result = validator.validate(loginRaw = "  abc  ", passwordRaw = "  12345678  ")

        assertFalse(result.isValid)
        assertEquals(AuthCredentialsValidationErrorType.TooShort, result.loginError?.type)
        assertEquals(AuthCredentialsValidationErrorType.MissingLetter, result.passwordError?.type)
    }

    @Test
    fun validate_passwordPriority_returnsInvalidCharactersBeforeCompositionChecks() {
        val result = validator.validate(loginRaw = "valid", passwordRaw = "pass12!@")

        assertFalse(result.isValid)
        assertEquals(AuthCredentialsValidationErrorType.InvalidCharacters, result.passwordError?.type)
        assertEquals(
            "Пароль может содержать только латинские буквы, цифры и символы . _ -.",
            result.passwordError?.message,
        )
    }

    @Test
    fun validate_passwordWithoutDigit_returnsMissingDigitError() {
        val result = validator.validate(loginRaw = "valid", passwordRaw = "password")

        assertFalse(result.isValid)
        assertEquals(AuthCredentialsValidationErrorType.MissingDigit, result.passwordError?.type)
    }

    @Test
    fun validate_matrixCoversLengthCharsetAndComposition() {
        data class Case(
            val login: String,
            val password: String,
            val expectedLoginError: AuthCredentialsValidationErrorType?,
            val expectedPasswordError: AuthCredentialsValidationErrorType?,
        )

        val cases =
            listOf(
                Case(
                    login = "a_b-",
                    password = "A1bcdefg",
                    expectedLoginError = null,
                    expectedPasswordError = null,
                ),
                Case(
                    login = "ab",
                    password = "A1bcdefg",
                    expectedLoginError = AuthCredentialsValidationErrorType.TooShort,
                    expectedPasswordError = null,
                ),
                Case(
                    login = "good login",
                    password = "A1bcdefg",
                    expectedLoginError = AuthCredentialsValidationErrorType.InvalidCharacters,
                    expectedPasswordError = null,
                ),
                Case(
                    login = "valid",
                    password = "ABCDEFGH",
                    expectedLoginError = null,
                    expectedPasswordError = AuthCredentialsValidationErrorType.MissingDigit,
                ),
                Case(
                    login = "valid",
                    password = "12345678",
                    expectedLoginError = null,
                    expectedPasswordError = AuthCredentialsValidationErrorType.MissingLetter,
                ),
                Case(
                    login = "valid",
                    password = "Abcdef!1",
                    expectedLoginError = null,
                    expectedPasswordError = AuthCredentialsValidationErrorType.InvalidCharacters,
                ),
            )

        cases.forEach { case ->
            val result = validator.validate(loginRaw = case.login, passwordRaw = case.password)
            assertEquals(case.expectedLoginError, result.loginError?.type)
            assertEquals(case.expectedPasswordError, result.passwordError?.type)
            assertEquals(
                case.expectedLoginError == null && case.expectedPasswordError == null,
                result.isValid,
            )
        }
    }
}
