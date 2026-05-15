package com.devpulse.app.data.repository

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
import com.devpulse.app.domain.repository.AuthResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAuthRepositoryTest {
    @Test
    fun login_mapsRemoteSuccessToDomainSuccess() =
        runTest {
            val remote =
                FakeRemoteDataSource(
                    loginResult = RemoteCallResult.Success(data = Unit, statusCode = 200),
                )
            val repository = DefaultAuthRepository(remoteDataSource = remote)

            val result = repository.login(login = "moksem", password = "secret")

            assertTrue(result is AuthResult.Success)
            assertEquals(1, remote.loginCalls)
            assertEquals(0, remote.registerCalls)
        }

    @Test
    fun register_mapsRemoteFailureToDomainFailure() =
        runTest {
            val remote =
                FakeRemoteDataSource(
                    registerResult =
                        RemoteCallResult.ApiFailure(
                            error = ApiError(kind = ApiErrorKind.BadRequest, userMessage = "Неверные данные"),
                            statusCode = 400,
                        ),
                )
            val repository = DefaultAuthRepository(remoteDataSource = remote)

            val result = repository.register(login = "moksem", password = "bad")

            assertTrue(result is AuthResult.Failure)
            assertEquals("Неверные данные", (result as AuthResult.Failure).error.userMessage)
            assertEquals(1, remote.registerCalls)
        }

    @Test
    fun login_doesNotFallbackToRegister_whenLoginFails() =
        runTest {
            val remote =
                FakeRemoteDataSource(
                    loginResult =
                        RemoteCallResult.ApiFailure(
                            error = ApiError(kind = ApiErrorKind.BadRequest, userMessage = "Логин отклонен"),
                            statusCode = 400,
                        ),
                )
            val repository = DefaultAuthRepository(remoteDataSource = remote)

            val result = repository.login(login = "moksem", password = "bad")

            assertTrue(result is AuthResult.Failure)
            assertEquals("Логин отклонен", (result as AuthResult.Failure).error.userMessage)
            assertEquals(1, remote.loginCalls)
            assertEquals(0, remote.registerCalls)
        }

    private class FakeRemoteDataSource(
        private val loginResult: RemoteCallResult<Unit> = RemoteCallResult.Success(data = Unit, statusCode = 200),
        private val registerResult: RemoteCallResult<Unit> = RemoteCallResult.Success(data = Unit, statusCode = 200),
    ) : DevPulseRemoteDataSource {
        var loginCalls: Int = 0
            private set
        var registerCalls: Int = 0
            private set

        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            loginCalls += 1
            return loginResult
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            registerCalls += 1
            return registerResult
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
