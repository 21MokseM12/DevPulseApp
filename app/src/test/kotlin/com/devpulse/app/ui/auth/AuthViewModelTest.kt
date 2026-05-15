package com.devpulse.app.ui.auth

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.ui.main.MainDispatcherRule
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
    fun submitLogin_withBlankFields_showsLoginValidationError() {
        runTest {
            val remote = FakeRemoteDataSource()
            val viewModel = AuthViewModel(remote)

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals("Для входа заполните логин и пароль.", viewModel.uiState.value.errorMessage)
            assertEquals(0, remote.registerCalls)
        }
    }

    @Test
    fun onLoginChanged_clearsPreviousError() {
        runTest {
            val viewModel = AuthViewModel(FakeRemoteDataSource())

            viewModel.submitLogin()
            assertEquals("Для входа заполните логин и пароль.", viewModel.uiState.value.errorMessage)

            viewModel.onLoginChanged("moksem")

            assertEquals(null, viewModel.uiState.value.errorMessage)
        }
    }

    @Test
    fun submit_trimsLoginBeforeSendingRequest() {
        runTest {
            val remote = FakeRemoteDataSource(result = RemoteCallResult.Success(data = Unit, statusCode = 200))
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("  moksem  ")
            viewModel.onPasswordChanged("  secret  ")

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals("moksem", remote.lastRegisterLogin)
            assertEquals("secret", remote.lastRegisterPassword)
            assertEquals("moksem", viewModel.uiState.value.login)
            assertTrue(viewModel.uiState.value.isAuthorized)
        }
    }

    @Test
    fun submitRegister_withSuccess_setsAuthorizedState() {
        runTest {
            val remote = FakeRemoteDataSource(result = RemoteCallResult.Success(data = Unit, statusCode = 200))
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitRegister()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isAuthorized)
            assertEquals(1, remote.registerCalls)
            assertEquals("moksem", remote.lastRegisterLogin)
            assertEquals("secret", remote.lastRegisterPassword)
            assertEquals("", viewModel.uiState.value.password)
        }
    }

    @Test
    fun submitLogin_withApiError_exposesActionScopedUserMessage() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    result =
                        RemoteCallResult.ApiFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Неверные данные",
                                ),
                            statusCode = 400,
                        ),
                )
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.errorMessage)
        }
    }

    @Test
    fun submitLogin_thenSubmitRegisterWhileLoading_ignoresSecondRequest() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = FakeRemoteDataSource(gate = gate)
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            viewModel.submitRegister()
            runCurrent()
            assertEquals(1, remote.registerCalls)
            assertEquals(AuthAction.Login, viewModel.uiState.value.loadingAction)

            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isAuthorized)
        }
    }

    @Test
    fun submitRegister_withNetworkError_exposesActionScopedUserMessage() {
        runTest {
            val remote =
                FakeRemoteDataSource(
                    result =
                        RemoteCallResult.NetworkFailure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.NetworkTimeout,
                                    userMessage = "Превышено время ожидания сети",
                                ),
                            throwable = IllegalStateException("timeout"),
                        ),
                )
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitRegister()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals(
                "Не удалось зарегистрироваться. Превышено время ожидания сети",
                viewModel.uiState.value.errorMessage,
            )
        }
    }

    @Test
    fun onAuthorizationHandled_resetsAuthorizationFlagAndPassword() {
        runTest {
            val remote = FakeRemoteDataSource(result = RemoteCallResult.Success(data = Unit, statusCode = 200))
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submitLogin()
            advanceUntilIdle()
            viewModel.onAuthorizationHandled()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals("", viewModel.uiState.value.password)
        }
    }

    @Test
    fun submitRegister_afterLoginError_clearsErrorAndStartsLoading() {
        runTest {
            val remote = FakeRemoteDataSource()
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            remote.nextResult =
                RemoteCallResult.ApiFailure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = "Неверные данные",
                        ),
                    statusCode = 400,
                )
            viewModel.submitLogin()
            advanceUntilIdle()
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.errorMessage)

            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            remote.nextResult = RemoteCallResult.Success(data = Unit, statusCode = 200)
            viewModel.submitRegister()
            runCurrent()

            assertEquals(null, viewModel.uiState.value.errorMessage)
            assertEquals(AuthAction.Register, viewModel.uiState.value.loadingAction)
            assertEquals(2, remote.registerCalls)

            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isAuthorized)
        }
    }

    private class FakeRemoteDataSource(
        result: RemoteCallResult<Unit> = RemoteCallResult.Success(data = Unit, statusCode = 200),
        gate: CompletableDeferred<Unit>? = null,
    ) : DevPulseRemoteDataSource {
        var nextResult: RemoteCallResult<Unit> = result
        var gate: CompletableDeferred<Unit>? = gate
        var registerCalls: Int = 0
            private set
        var lastRegisterLogin: String? = null
            private set
        var lastRegisterPassword: String? = null
            private set

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            registerCalls += 1
            lastRegisterLogin = request.login
            lastRegisterPassword = request.password
            gate?.await()
            return nextResult
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            throw UnsupportedOperationException()
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            throw UnsupportedOperationException()
        }
    }
}
