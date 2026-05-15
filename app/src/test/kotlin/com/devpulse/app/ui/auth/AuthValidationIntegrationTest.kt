package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
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
            assertEquals("Логин должен содержать минимум 4 символа.", viewModel.uiState.value.loginInlineError)
            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
        }

    @Test
    fun backendValidationError_mapsToSingleInlineMessage_withoutGlobalConflict() =
        runTest {
            val repository = CountingAuthRepository()
            repository.loginResult =
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = "password must include a number",
                        ),
                )
            val viewModel =
                AuthViewModel(
                    loginClientUseCase = LoginClientUseCase(repository),
                    registerClientUseCase = RegisterClientUseCase(repository),
                )
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("valid123")

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(1, repository.loginCalls)
            assertEquals(
                "Проверьте пароль. password must include a number",
                viewModel.uiState.value.passwordInlineError,
            )
            assertEquals(null, viewModel.uiState.value.loginInlineError)
            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
        }

    private class CountingAuthRepository : AuthRepository {
        var loginCalls: Int = 0
        var registerCalls: Int = 0
        var loginResult: AuthResult = AuthResult.Success
        var registerResult: AuthResult = AuthResult.Success

        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            loginCalls += 1
            return loginResult
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            registerCalls += 1
            return registerResult
        }
    }
}
