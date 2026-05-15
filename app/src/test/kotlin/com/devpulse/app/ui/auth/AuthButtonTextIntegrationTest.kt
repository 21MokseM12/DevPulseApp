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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthButtonTextIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loginError_thenRegisterSuccess_switchesButtonTextByAction() =
        runTest {
            val remote = SequenceRemoteDataSource()
            val viewModel = AuthViewModel(remote)
            viewModel.onLoginChanged("moksem")
            viewModel.onPasswordChanged("secret")

            remote.enqueueLogin(
                RemoteCallResult.ApiFailure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = "Неверные данные",
                        ),
                    statusCode = 400,
                ),
            )
            remote.enqueueRegister(RemoteCallResult.Success(data = Unit, statusCode = 200))

            viewModel.submitLogin()
            advanceUntilIdle()

            assertEquals(AuthButtonStatus.Error, viewModel.uiState.value.loginButtonState.status)
            assertEquals("Повторить вход", viewModel.uiState.value.loginButtonState.text)
            assertEquals(AuthButtonStatus.Idle, viewModel.uiState.value.registerButtonState.status)
            assertEquals("Не удалось войти. Неверные данные", viewModel.uiState.value.loginErrorMessage)
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
            assertTrue(viewModel.uiState.value.isAuthorized)
        }

    private class SequenceRemoteDataSource : DevPulseRemoteDataSource {
        private val loginResults = ArrayDeque<RemoteCallResult<Unit>>()
        private val registerResults = ArrayDeque<RemoteCallResult<Unit>>()

        fun enqueueLogin(result: RemoteCallResult<Unit>) {
            loginResults.addLast(result)
        }

        fun enqueueRegister(result: RemoteCallResult<Unit>) {
            registerResults.addLast(result)
        }

        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return loginResults.removeFirstOrNull() ?: RemoteCallResult.Success(data = Unit, statusCode = 200)
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return registerResults.removeFirstOrNull() ?: RemoteCallResult.Success(data = Unit, statusCode = 200)
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
