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
class AuthButtonTextIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginError_thenRegisterSuccess_switchesButtonTextByAction() =
        runTest {
            val remote = SequenceAuthRepository()
            val viewModel =
                AuthViewModel(
                    loginClientUseCase = LoginClientUseCase(remote),
                    registerClientUseCase = RegisterClientUseCase(remote),
                )
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret12")

            remote.enqueueLogin(
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = "Неверные данные",
                        ),
                ),
            )
            remote.enqueueRegister(AuthResult.Success)

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Повторить вход", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
            assertEquals(
                "Не удалось войти. Неверные данные Проверьте введенные данные и повторите попытку.",
                viewModel.uiState.value.loginErrorMessage,
            )
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthAction.Login, viewModel.uiState.value.lastSubmittedAction)

            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Регистрация выполнена", viewModel.uiState.value.registerButtonState.text)
            assertEquals(null, viewModel.uiState.value.loginErrorMessage)
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthAction.Register, viewModel.uiState.value.lastSubmittedAction)
            assertEquals(AuthAction.Register, viewModel.uiState.value.pendingAuthSuccess?.action)
            assertEquals("moksem", viewModel.uiState.value.pendingAuthSuccess?.login)
        }

    @Test
    fun loginNetworkFailure_thenRetrySuccess_resetsErrorAndKeepsActionStateIsolated() =
        runTest {
            val remote = SequenceAuthRepository()
            val viewModel =
                AuthViewModel(
                    loginClientUseCase = LoginClientUseCase(remote),
                    registerClientUseCase = RegisterClientUseCase(remote),
                )
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret12")

            remote.enqueueLogin(
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.NetworkTimeout,
                            userMessage = "Нет соединения",
                        ),
                ),
            )
            remote.enqueueLogin(AuthResult.Success)

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Повторить вход", viewModel.uiState.value.loginButtonState.text)
            assertEquals(
                "Не удалось войти. Превышено время ожидания ответа сервера. Проверьте сеть и попробуйте снова.",
                viewModel.uiState.value.loginErrorMessage,
            )
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.loginErrorMessage)
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Вход выполнен", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
            assertEquals(AuthAction.Login, viewModel.uiState.value.pendingAuthSuccess?.action)
        }

    @Test
    fun loginNotFoundFailure_showsAccountNotFoundGuidance() =
        runTest {
            val remote = SequenceAuthRepository()
            val viewModel =
                AuthViewModel(
                    loginClientUseCase = LoginClientUseCase(remote),
                    registerClientUseCase = RegisterClientUseCase(remote),
                )
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret12")
            remote.enqueueLogin(
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.NotFound,
                            userMessage = "Endpoint not found",
                        ),
                ),
            )

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(
                "Не удалось войти. Аккаунт с таким логином не найден.",
                viewModel.uiState.value.loginErrorMessage,
            )
            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
        }

    private class SequenceAuthRepository : AuthRepository {
        private val loginResults = ArrayDeque<AuthResult>()
        private val registerResults = ArrayDeque<AuthResult>()

        fun enqueueLogin(result: AuthResult) {
            loginResults.addLast(result)
        }

        fun enqueueRegister(result: AuthResult) {
            registerResults.addLast(result)
        }

        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            return loginResults.removeFirstOrNull() ?: AuthResult.Success
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            return registerResults.removeFirstOrNull() ?: AuthResult.Success
        }
    }
}
