package com.devpulse.app.ui.auth

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.repository.AuthRepository
import com.devpulse.app.domain.repository.AuthResult
import com.devpulse.app.domain.usecase.LoginClientUseCase
import com.devpulse.app.domain.usecase.RegisterClientUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialState_exposesIdleTextsForBothButtons() {
        runTest {
            val viewModel = createViewModel(FakeAuthRepository())

            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Войти", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Зарегистрироваться", viewModel.uiState.value.registerButtonState.text)
        }
    }

    @Test
    fun submitLogin_withBlankFields_showsLoginValidationError() {
        runTest {
            val remote = FakeAuthRepository()
            val viewModel = createViewModel(remote)

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals("Для входа заполните логин и пароль.", viewModel.uiState.value.activeErrorMessage)
            assertEquals("Для входа заполните логин и пароль.", viewModel.uiState.value.loginErrorMessage)
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Повторить вход", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
            assertEquals(0, remote.loginCalls)
            assertEquals(0, remote.registerCalls)
        }
    }

    @Test
    fun submitRegister_withBlankFields_showsRegisterValidationError() {
        runTest {
            val remote = FakeAuthRepository()
            val viewModel = createViewModel(remote)

            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals("Для регистрации заполните логин и пароль.", viewModel.uiState.value.activeErrorMessage)
            assertEquals(null, viewModel.uiState.value.loginErrorMessage)
            assertEquals("Для регистрации заполните логин и пароль.", viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Повторить регистрацию", viewModel.uiState.value.registerButtonState.text)
            assertEquals(0, remote.loginCalls)
            assertEquals(0, remote.registerCalls)
        }
    }

    @Test
    fun onLoginChanged_clearsPreviousError() {
        runTest {
            val viewModel = createViewModel(FakeAuthRepository())

            viewModel.submitLogin()
            assertEquals("Для входа заполните логин и пароль.", viewModel.uiState.value.activeErrorMessage)

            viewModel.onLoginChanged("moksem")

            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
        }
    }

    @Test
    fun submit_trimsLoginBeforeSendingRequest() {
        runTest {
            val remote = FakeAuthRepository(result = AuthResult.Success)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("  moksem  ")
            viewModel.onPasswordChanged("  secret  ")

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals("moksem", remote.lastLoginLogin)
            assertEquals("secret", remote.lastLoginPassword)
            assertEquals("moksem", viewModel.uiState.value.login)
            assertEquals(AuthAction.Login, viewModel.uiState.value.pendingAuthSuccess?.action)
            assertEquals("moksem", viewModel.uiState.value.pendingAuthSuccess?.login)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Вход выполнен", viewModel.uiState.value.loginButtonState.text)
        }
    }

    @Test
    fun submitRegister_withSuccess_setsAuthorizedState() {
        runTest {
            val remote = FakeAuthRepository(result = AuthResult.Success)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(AuthAction.Register, viewModel.uiState.value.pendingAuthSuccess?.action)
            assertEquals("moksem", viewModel.uiState.value.pendingAuthSuccess?.login)
            assertEquals(1, remote.registerCalls)
            assertEquals("moksem", remote.lastRegisterLogin)
            assertEquals("secret", remote.lastRegisterPassword)
            assertEquals("", viewModel.uiState.value.password)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Регистрация выполнена", viewModel.uiState.value.registerButtonState.text)
        }
    }

    @Test
    fun submitLogin_withApiError_exposesActionScopedUserMessage() {
        runTest {
            val remote =
                FakeAuthRepository(
                    result =
                        AuthResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Неверные данные",
                                ),
                        ),
                )
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.pendingAuthSuccess)
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.activeErrorMessage)
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.loginErrorMessage)
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)
            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Повторить вход", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
        }
    }

    @Test
    fun submitLogin_thenSubmitRegisterWhileLoading_ignoresSecondRequest() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = FakeAuthRepository(gate = gate)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            viewModel.submitRegister()
            runCurrent()
            assertEquals(1, remote.loginCalls)
            assertEquals(0, remote.registerCalls)
            assertEquals(AuthAction.Login, viewModel.uiState.value.lastSubmittedAction)
            assertTrue(viewModel.uiState.value.isLoginLoading)
            assertFalse(viewModel.uiState.value.isRegisterLoading)
            assertEquals(AuthButtonStatus.Loading, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Входим...", viewModel.uiState.value.loginButtonState.text)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(AuthAction.Login, viewModel.uiState.value.pendingAuthSuccess?.action)
        }
    }

    @Test
    fun submitRegister_withNetworkError_exposesActionScopedUserMessage() {
        runTest {
            val remote =
                FakeAuthRepository(
                    result =
                        AuthResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.NetworkTimeout,
                                    userMessage = "Превышено время ожидания сети",
                                ),
                        ),
                )
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.pendingAuthSuccess)
            assertEquals(
                "Не удалось зарегистрироваться. Превышено время ожидания сети",
                viewModel.uiState.value.activeErrorMessage,
            )
            assertEquals(null, viewModel.uiState.value.loginErrorMessage)
            assertEquals(
                "Не удалось зарегистрироваться. Превышено время ожидания сети",
                viewModel.uiState.value.registerErrorMessage,
            )
            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Повторить регистрацию", viewModel.uiState.value.registerButtonState.text)
        }
    }

    @Test
    fun onAuthSuccessHandled_clearsPendingSuccessAndPassword() {
        runTest {
            val remote = FakeAuthRepository(result = AuthResult.Success)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            advanceUntilIdle()
            viewModel.onAuthSuccessHandled()

            assertEquals(null, viewModel.uiState.value.pendingAuthSuccess)
            assertEquals("", viewModel.uiState.value.password)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
        }
    }

    @Test
    fun submitRegister_afterLoginError_clearsErrorAndStartsLoading() {
        runTest {
            val remote = FakeAuthRepository()
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            remote.nextResult =
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = "Неверные данные",
                        ),
                )
            viewModel.submitLogin()
            advanceUntilIdle()
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.activeErrorMessage)
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.loginErrorMessage)
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)

            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            remote.nextResult = AuthResult.Success
            viewModel.submitRegister()
            runCurrent()

            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
            assertEquals(AuthAction.Register, viewModel.uiState.value.lastSubmittedAction)
            assertFalse(viewModel.uiState.value.isLoginLoading)
            assertTrue(viewModel.uiState.value.isRegisterLoading)
            assertEquals(AuthButtonStatus.Loading, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Регистрируем...", viewModel.uiState.value.registerButtonState.text)
            assertEquals(1, remote.loginCalls)
            assertEquals(1, remote.registerCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(AuthAction.Register, viewModel.uiState.value.pendingAuthSuccess?.action)
        }
    }

    @Test
    fun submitLogin_afterNetworkError_retrySucceedsWithoutSharingRegisterError() {
        runTest {
            val remote = FakeAuthRepository()
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")
            remote.nextResult =
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.NetworkTimeout,
                            userMessage = "Превышено время ожидания сети",
                        ),
                )

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(
                "Не удалось войти. Превышено время ожидания сети",
                viewModel.uiState.value.loginErrorMessage,
            )
            assertEquals(null, viewModel.uiState.value.registerErrorMessage)

            remote.nextResult = AuthResult.Success
            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(2, remote.loginCalls)
            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
            assertEquals(AuthAction.Login, viewModel.uiState.value.pendingAuthSuccess?.action)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.loginButtonState.status)
        }
    }

    @Test
    fun submitRegister_afterNetworkError_retrySucceedsWithoutSharingLoginError() {
        runTest {
            val remote = FakeAuthRepository()
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")
            remote.nextResult =
                AuthResult.Failure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.NetworkTimeout,
                            userMessage = "Превышено время ожидания сети",
                        ),
                )

            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.loginErrorMessage)
            assertEquals(
                "Не удалось зарегистрироваться. Превышено время ожидания сети",
                viewModel.uiState.value.registerErrorMessage,
            )

            remote.nextResult = AuthResult.Success
            viewModel.submitRegister()
            advanceUntilIdle()

            assertEquals(2, remote.registerCalls)
            assertEquals(null, viewModel.uiState.value.activeErrorMessage)
            assertEquals(AuthAction.Register, viewModel.uiState.value.pendingAuthSuccess?.action)
            assertEquals(AuthButtonStatus.Success, viewModel.uiState.value.registerButtonState.status)
        }
    }

    @Test
    fun onFieldChange_whileLoading_doesNotMutateCredentialsOrLoadingState() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = FakeAuthRepository(gate = gate)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitRegister()
            runCurrent()
            assertTrue(viewModel.uiState.value.isRegisterLoading)

            viewModel.onLoginChanged("updated")
            viewModel.onPasswordChanged("updated-password")

            assertEquals("moksem", viewModel.uiState.value.login)
            assertEquals("secret", viewModel.uiState.value.password)
            assertTrue(viewModel.uiState.value.isRegisterLoading)
            assertEquals(AuthButtonStatus.Loading, viewModel.uiState.value.registerButtonState.status)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(AuthAction.Register, viewModel.uiState.value.pendingAuthSuccess?.action)
        }
    }

    @Test
    fun authRequest_cancelPendingCall_stopsLoadingAndCancelsRequest() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = FakeAuthRepository(gate = gate)
            val viewModel = createViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoginLoading)
            assertEquals(1, remote.loginCalls)

            viewModel.cancelPendingAuthRequest()
            advanceUntilIdle()

            assertTrue(remote.loginAwaitCancelled)
            assertFalse(gate.isCompleted)
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.loginButtonState.status)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
        }
    }

    private fun createViewModel(repository: AuthRepository): AuthViewModel {
        return AuthViewModel(
            loginClientUseCase = LoginClientUseCase(repository),
            registerClientUseCase = RegisterClientUseCase(repository),
        )
    }

    private class FakeAuthRepository(
        result: AuthResult = AuthResult.Success,
        gate: CompletableDeferred<Unit>? = null,
    ) : AuthRepository {
        var nextResult: AuthResult = result
        var gate: CompletableDeferred<Unit>? = gate
        var loginCalls: Int = 0
            private set
        var registerCalls: Int = 0
            private set
        var lastLoginLogin: String? = null
            private set
        var lastLoginPassword: String? = null
            private set
        var lastRegisterLogin: String? = null
            private set
        var lastRegisterPassword: String? = null
            private set
        var loginAwaitCancelled: Boolean = false
            private set
        var registerAwaitCancelled: Boolean = false
            private set

        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            loginCalls += 1
            lastLoginLogin = login
            lastLoginPassword = password
            try {
                gate?.await()
            } catch (exception: CancellationException) {
                loginAwaitCancelled = true
                throw exception
            }
            return nextResult
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            registerCalls += 1
            lastRegisterLogin = login
            lastRegisterPassword = password
            try {
                gate?.await()
            } catch (exception: CancellationException) {
                registerAwaitCancelled = true
                throw exception
            }
            return nextResult
        }
    }
}
