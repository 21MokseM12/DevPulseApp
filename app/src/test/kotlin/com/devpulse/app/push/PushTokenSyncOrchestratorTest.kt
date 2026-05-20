package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.PendingPushTokenSync
import com.devpulse.app.data.local.preferences.PushTokenSyncAction
import com.devpulse.app.data.local.preferences.PushTokenSyncStateStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.DeviceTokenRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.PushTokenDeactivateRequestDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushTokenSyncOrchestratorTest {
    @Test
    fun queueRegister_success_clearsPendingState() =
        runTest {
            val stateStore = InMemoryPushTokenSyncStateStore()
            val remote = FakeRemoteDataSource(registerResponses = listOf(RemoteCallResult.Success(Unit, 200)))
            val analytics = RecordingPushAnalyticsLogger()
            val orchestrator =
                PushTokenSyncOrchestrator(
                    remoteDataSource = remote,
                    stateStore = stateStore,
                    sessionStore = FakeSessionStore(login = "moksem"),
                    metadataProvider = StaticPushTokenMetadataProvider(),
                    analyticsLogger = analytics,
                )

            orchestrator.queueRegister(token = "abc", reason = "test")

            assertEquals(1, remote.registerCalls)
            assertNull(stateStore.pending)
            assertEquals("1.73.0", remote.lastRegisterRequest?.appVersion)
            assertEquals("device-1", remote.lastRegisterRequest?.deviceId)
            assertEquals(1, analytics.tokenRegisteredCalls)
        }

    @Test
    fun queueRegister_temporaryFailure_keepsPendingStateForNextAttempt() =
        runTest {
            val stateStore = InMemoryPushTokenSyncStateStore()
            val remote =
                FakeRemoteDataSource(
                    registerResponses =
                        listOf(
                            RemoteCallResult.NetworkFailure(
                                error = ApiError(ApiErrorKind.NetworkTimeout, "timeout"),
                                throwable = RuntimeException("timeout"),
                            ),
                            RemoteCallResult.NetworkFailure(
                                error = ApiError(ApiErrorKind.NetworkTimeout, "timeout"),
                                throwable = RuntimeException("timeout"),
                            ),
                            RemoteCallResult.NetworkFailure(
                                error = ApiError(ApiErrorKind.NetworkTimeout, "timeout"),
                                throwable = RuntimeException("timeout"),
                            ),
                        ),
                )
            val orchestrator =
                PushTokenSyncOrchestrator(
                    remoteDataSource = remote,
                    stateStore = stateStore,
                    sessionStore = FakeSessionStore(login = "moksem"),
                    metadataProvider = StaticPushTokenMetadataProvider(),
                    analyticsLogger = RecordingPushAnalyticsLogger(),
                )

            orchestrator.queueRegister(token = "abc", reason = "test")

            assertEquals(3, remote.registerCalls)
            assertEquals(
                PendingPushTokenSync(
                    action = PushTokenSyncAction.Register,
                    token = "abc",
                ),
                stateStore.pending,
            )
        }

    @Test
    fun queueUnregister_nonRetryableFailure_dropsPendingState() =
        runTest {
            val stateStore = InMemoryPushTokenSyncStateStore()
            val remote =
                FakeRemoteDataSource(
                    unregisterResponses =
                        listOf(
                            RemoteCallResult.ApiFailure(
                                error = ApiError(ApiErrorKind.BadRequest, "bad request", statusCode = 400),
                                statusCode = 400,
                            ),
                        ),
                )
            val orchestrator =
                PushTokenSyncOrchestrator(
                    remoteDataSource = remote,
                    stateStore = stateStore,
                    sessionStore = FakeSessionStore(login = "moksem"),
                    metadataProvider = StaticPushTokenMetadataProvider(),
                    analyticsLogger = RecordingPushAnalyticsLogger(),
                )

            orchestrator.queueUnregister(token = "abc", reason = "logout")

            assertEquals(1, remote.unregisterCalls)
            assertEquals("abc", remote.lastUnregisterRequest?.token)
            assertNull(stateStore.pending)
        }

    @Test
    fun queueRegister_withoutSession_doesNotCallBackendAndKeepsPendingState() =
        runTest {
            val stateStore = InMemoryPushTokenSyncStateStore()
            val remote = FakeRemoteDataSource(registerResponses = listOf(RemoteCallResult.Success(Unit, 200)))
            val orchestrator =
                PushTokenSyncOrchestrator(
                    remoteDataSource = remote,
                    stateStore = stateStore,
                    sessionStore = FakeSessionStore(login = null),
                    metadataProvider = StaticPushTokenMetadataProvider(),
                    analyticsLogger = RecordingPushAnalyticsLogger(),
                )

            orchestrator.queueRegister(token = "abc", reason = "token_refresh")

            assertEquals(0, remote.registerCalls)
            assertEquals(
                PendingPushTokenSync(
                    action = PushTokenSyncAction.Register,
                    token = "abc",
                ),
                stateStore.pending,
            )
        }

    private class InMemoryPushTokenSyncStateStore : PushTokenSyncStateStore {
        var pending: PendingPushTokenSync? = null

        override suspend fun getPendingSync(): PendingPushTokenSync? = pending

        override suspend fun savePendingSync(sync: PendingPushTokenSync) {
            pending = sync
        }

        override suspend fun clearPendingSync() {
            pending = null
        }
    }

    private class FakeRemoteDataSource(
        private val registerResponses: List<RemoteCallResult<Unit>> = listOf(RemoteCallResult.Success(Unit, 200)),
        private val unregisterResponses: List<RemoteCallResult<Unit>> = listOf(RemoteCallResult.Success(Unit, 200)),
    ) : DevPulseRemoteDataSource {
        var registerCalls: Int = 0
        var unregisterCalls: Int = 0
        var lastRegisterRequest: DeviceTokenRequestDto? = null
        var lastUnregisterRequest: PushTokenDeactivateRequestDto? = null

        override suspend fun registerDeviceToken(request: DeviceTokenRequestDto): RemoteCallResult<Unit> {
            val index = registerCalls
            registerCalls += 1
            lastRegisterRequest = request
            return registerResponses.getOrElse(index) { registerResponses.last() }
        }

        override suspend fun unregisterDeviceToken(request: PushTokenDeactivateRequestDto): RemoteCallResult<Unit> {
            val index = unregisterCalls
            unregisterCalls += 1
            lastUnregisterRequest = request
            return unregisterResponses.getOrElse(index) { unregisterResponses.last() }
        }

        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return RemoteCallResult.Success(emptyList(), 200)
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("not used")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("not used")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            error("not used")
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            error("not used")
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            error("not used")
        }
    }

    private class StaticPushTokenMetadataProvider : PushTokenMetadataSource {
        override fun getMetadata(): PushTokenMetadata = PushTokenMetadata(appVersion = "1.73.0", deviceId = "device-1")
    }

    private class FakeSessionStore(login: String?) : SessionStore {
        private val state =
            MutableStateFlow(
                login?.let {
                    StoredSession(
                        login = it,
                        isRegistered = true,
                        updatedAtEpochMs = 1L,
                    )
                },
            )

        override fun observeSession(): Flow<StoredSession?> = state

        override fun observeClientLogin(): Flow<String?> = state.map { it?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            state.value =
                StoredSession(
                    login = login,
                    isRegistered = isRegistered,
                    updatedAtEpochMs = 1L,
                )
        }

        override suspend fun clearSession() {
            state.value = null
        }
    }

    private class RecordingPushAnalyticsLogger : PushAnalyticsTracker {
        var tokenRegisteredCalls: Int = 0

        override fun tokenRegistered(
            statusCode: Int,
            reason: String,
            source: String,
        ) {
            tokenRegisteredCalls += 1
        }

        override fun tokenRefresh(source: String) = Unit

        override fun pushReceived(
            result: PushHandleResult,
            messageId: String?,
        ) = Unit

        override fun pushOpened(
            eventId: String?,
            url: String?,
        ) = Unit
    }
}
