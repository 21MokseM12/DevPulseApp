package com.devpulse.app.domain.usecase

import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.ParsedPushUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class AccountLifecycleUseCaseTest {
    @Test
    fun unregister_success_clearsAllLocalState() =
        runTest {
            val sessionStore = FakeSessionStore()
            val updatesRepository = FakeUpdatesRepository()
            val pushTokenStore = FakePushTokenStore()
            val permissionStore = FakeNotificationPermissionStore()
            val remote = FakeRemoteDataSource(RemoteCallResult.Success(Unit, 200))
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = sessionStore,
                    updatesRepository = updatesRepository,
                    pushTokenStore = pushTokenStore,
                    notificationPermissionStore = permissionStore,
                )

            val result = useCase.unregister()

            assertEquals(AccountLifecycleResult.Success, result)
            assertEquals(1, remote.unregisterCalls)
            assertFalse(sessionStore.hasSession())
            assertTrue(updatesRepository.isCleared)
            assertTrue(pushTokenStore.isCleared)
            assertFalse(permissionStore.hasRequested())
        }

    @Test
    fun unregister_404_clearsAllLocalState() =
        runTest {
            val sessionStore = FakeSessionStore()
            val updatesRepository = FakeUpdatesRepository()
            val pushTokenStore = FakePushTokenStore()
            val permissionStore = FakeNotificationPermissionStore()
            val remote =
                FakeRemoteDataSource(
                    RemoteCallResult.ApiFailure(
                        error = ApiError(ApiErrorKind.NotFound, "not found", statusCode = 404),
                        statusCode = 404,
                    ),
                )
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = sessionStore,
                    updatesRepository = updatesRepository,
                    pushTokenStore = pushTokenStore,
                    notificationPermissionStore = permissionStore,
                )

            val result = useCase.unregister()

            assertEquals(AccountLifecycleResult.Success, result)
            assertEquals(1, remote.unregisterCalls)
            assertFalse(sessionStore.hasSession())
            assertTrue(updatesRepository.isCleared)
            assertTrue(pushTokenStore.isCleared)
        }

    @Test
    fun unregister_timeout_returnsFailureAndKeepsState() =
        runTest {
            val sessionStore = FakeSessionStore()
            val updatesRepository = FakeUpdatesRepository()
            val pushTokenStore = FakePushTokenStore()
            val permissionStore = FakeNotificationPermissionStore()
            val timeoutError = ApiError(ApiErrorKind.NetworkTimeout, "timeout")
            val remote =
                FakeRemoteDataSource(
                    RemoteCallResult.NetworkFailure(
                        error = timeoutError,
                        throwable = RuntimeException("timeout"),
                    ),
                )
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = sessionStore,
                    updatesRepository = updatesRepository,
                    pushTokenStore = pushTokenStore,
                    notificationPermissionStore = permissionStore,
                )

            val result = useCase.unregister()

            assertTrue(result is AccountLifecycleResult.Failure)
            assertEquals(timeoutError, (result as AccountLifecycleResult.Failure).error)
            assertTrue(sessionStore.hasSession())
            assertFalse(updatesRepository.isCleared)
            assertFalse(pushTokenStore.isCleared)
            assertTrue(permissionStore.hasRequested())
        }

    @Test
    fun unregister_cancellation_returnsCancelledAndKeepsState() =
        runTest {
            val sessionStore = FakeSessionStore()
            val updatesRepository = FakeUpdatesRepository()
            val pushTokenStore = FakePushTokenStore()
            val permissionStore = FakeNotificationPermissionStore()
            val remote = ThrowingRemoteDataSource()
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = sessionStore,
                    updatesRepository = updatesRepository,
                    pushTokenStore = pushTokenStore,
                    notificationPermissionStore = permissionStore,
                )

            val result = useCase.unregister()

            assertEquals(AccountLifecycleResult.Cancelled, result)
            assertTrue(sessionStore.hasSession())
            assertFalse(updatesRepository.isCleared)
            assertFalse(pushTokenStore.isCleared)
            assertTrue(permissionStore.hasRequested())
        }

    private class FakeRemoteDataSource(
        private val unregisterResult: RemoteCallResult<Unit>,
    ) : DevPulseRemoteDataSource {
        var unregisterCalls: Int = 0
            private set

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            unregisterCalls += 1
            return unregisterResult
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return RemoteCallResult.Success(emptyList(), 200)
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("Not used")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto> =
            error("Not used")
    }

    private class ThrowingRemoteDataSource : DevPulseRemoteDataSource {
        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw CancellationException("cancelled")
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return RemoteCallResult.Success(emptyList(), 200)
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("Not used")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto> =
            error("Not used")
    }

    private class FakeSessionStore : SessionStore {
        private val state: MutableStateFlow<StoredSession?> =
            MutableStateFlow(
                StoredSession(
                    login = "moksem",
                    isRegistered = true,
                    updatedAtEpochMs = 1L,
                ),
            )

        override fun observeSession(): Flow<StoredSession?> = state.asStateFlow()

        override fun observeClientLogin(): Flow<String?> = state.asStateFlow().map { it?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) = Unit

        override suspend fun clearSession() {
            state.value = null
        }

        fun hasSession(): Boolean = state.value != null
    }

    private class FakeUpdatesRepository : UpdatesRepository {
        var isCleared: Boolean = false
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = MutableStateFlow(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() {
            isCleared = true
        }
    }

    private class FakePushTokenStore : PushTokenStore {
        var isCleared: Boolean = false
            private set

        private var token: String? = "token"

        override fun observeToken(): Flow<String?> = MutableStateFlow(token)

        override suspend fun getToken(): String? = token

        override suspend fun saveToken(token: String) {
            this.token = token
        }

        override suspend fun clearToken() {
            isCleared = true
            token = null
        }
    }

    private class FakeNotificationPermissionStore : NotificationPermissionStore {
        private val requested = MutableStateFlow(true)

        override fun observeHasRequested(): Flow<Boolean> = requested.asStateFlow()

        override suspend fun hasRequested(): Boolean = requested.value

        override suspend fun markRequested() {
            requested.value = true
        }

        override suspend fun clearRequestedFlag() {
            requested.value = false
        }
    }
}
