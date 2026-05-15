package com.devpulse.app.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCredentialsValidationPolicyTest {
    @Test
    fun loginExamples_followAllowedCharactersAndLengthRules() {
        val validLogins =
            listOf(
                "user",
                "user.name",
                "user_name",
                "user-name",
                "Abc9",
            )
        val invalidLogins =
            listOf(
                "abc",
                "a bcd",
                "юзер123",
                "name!",
                "abcd$",
            )

        validLogins.forEach { login ->
            assertTrue(
                "Expected login to pass policy checks: $login",
                login.length >= AuthCredentialsValidationPolicy.MIN_LOGIN_LENGTH &&
                    AuthCredentialsValidationPolicy.hasAllowedCharacters(login),
            )
        }
        invalidLogins.forEach { login ->
            val passesRules =
                login.length >= AuthCredentialsValidationPolicy.MIN_LOGIN_LENGTH &&
                    AuthCredentialsValidationPolicy.hasAllowedCharacters(login)
            assertFalse("Expected login to fail policy checks: $login", passesRules)
        }
    }

    @Test
    fun passwordExamples_requireAllowedCharactersAndComposition() {
        val validPasswords =
            listOf(
                "pass1234",
                "Abcd0000",
                "a1_b-c.d",
            )
        val invalidPasswords =
            listOf(
                "12345678",
                "abcdefgh",
                "ab12",
                "abc 1234",
                "пароль12",
                "pass12!@",
            )

        validPasswords.forEach { password ->
            assertTrue(
                "Expected password to pass policy checks: $password",
                password.length >= AuthCredentialsValidationPolicy.MIN_PASSWORD_LENGTH &&
                    AuthCredentialsValidationPolicy.hasAllowedCharacters(password) &&
                    AuthCredentialsValidationPolicy.hasLetter(password) &&
                    AuthCredentialsValidationPolicy.hasDigit(password),
            )
        }
        invalidPasswords.forEach { password ->
            val passesRules =
                password.length >= AuthCredentialsValidationPolicy.MIN_PASSWORD_LENGTH &&
                    AuthCredentialsValidationPolicy.hasAllowedCharacters(password) &&
                    AuthCredentialsValidationPolicy.hasLetter(password) &&
                    AuthCredentialsValidationPolicy.hasDigit(password)
            assertFalse("Expected password to fail policy checks: $password", passesRules)
        }
    }
}
