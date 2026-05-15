package com.devpulse.app.ui.auth

import com.devpulse.app.domain.repository.AuthRepository
import com.devpulse.app.domain.repository.AuthResult
import com.devpulse.app.domain.usecase.LoginClientUseCase
import com.devpulse.app.domain.usecase.RegisterClientUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthValidationIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidCredentials_doNotReachRepository_forLoginAndRegister() =
        runTest {
            val repository = CountingAuthRepository()
            val viewModel =
                AuthViewModel(
                    loginClientUseCase = LoginClientUseCase(repository),
                    registerClientUseCase = RegisterClientUseCase(repository),
                )
            viewModel.onLoginChanged("ab")
            viewModel.onPasswordChanged("12345678")

            viewModel.submitLogin()
            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(0, repository.loginCalls)
            assertEquals(0, repository.registerCalls)
            assertEquals("Для регистрации: Логин должен содержать минимум 4 символа.", viewModel.uiState.value.activeErrorMessage)
        }

    private class CountingAuthRepository : AuthRepository {
        var loginCalls: Int = 0
        var registerCalls: Int = 0

        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            loginCalls += 1
            return AuthResult.Success
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            registerCalls += 1
            return AuthResult.Success
        }
    }
}
