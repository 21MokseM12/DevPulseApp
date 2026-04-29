package com.devpulse.app.ui.auth

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
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
    fun submit_withBlankFields_showsValidationError() {
        runTest {
            val remote = FakeRemoteDataSource()
            val viewModel = AuthViewModel(remote)

            viewModel.submit()
            advanceUntilIdle()

            assertEquals("Заполните логин и пароль.", viewModel.uiState.value.errorMessage)
            assertEquals(0, remote.registerCalls)
        }
    }

    @Test
    fun submit_withSuccess_setsAuthorizedState() {
        runTest {
            val remote = FakeRemoteDataSource(result = RemoteCallResult.Success(data = Unit, statusCode = 200))
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submit()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isAuthorized)
            assertEquals(1, remote.registerCalls)
            assertEquals("moksem", remote.lastRegisterLogin)
        }
    }

    @Test
    fun submit_withApiError_exposesUserMessage() {
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

            viewModel.submit()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals("Неверные данные", viewModel.uiState.value.errorMessage)
        }
    }

    @Test
    fun submit_whileLoading_ignoresSecondRequest() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val remote = FakeRemoteDataSource(gate = gate)
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submit()
            viewModel.submit()
            runCurrent()
            assertEquals(1, remote.registerCalls)

            gate.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isAuthorized)
        }
    }

    @Test
    fun submit_withNetworkError_exposesUserMessage() {
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

            viewModel.submit()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals("Превышено время ожидания сети", viewModel.uiState.value.errorMessage)
        }
    }

    @Test
    fun onAuthorizationHandled_resetsAuthorizationFlagAndPassword() {
        runTest {
            val remote = FakeRemoteDataSource(result = RemoteCallResult.Success(data = Unit, statusCode = 200))
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            viewModel.submit()
            advanceUntilIdle()
            viewModel.onAuthorizationHandled()

            assertFalse(viewModel.uiState.value.isAuthorized)
            assertEquals("", viewModel.uiState.value.password)
        }
    }

    private class FakeRemoteDataSource(
        private val result: RemoteCallResult<Unit> = RemoteCallResult.Success(data = Unit, statusCode = 200),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : DevPulseRemoteDataSource {
        var registerCalls: Int = 0
            private set
        var lastRegisterLogin: String? = null
            private set

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            registerCalls += 1
            lastRegisterLogin = request.login
            gate?.await()
            return result
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

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            throw UnsupportedOperationException()
        }
    }
}
