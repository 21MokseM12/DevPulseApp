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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushTokenSyncSessionIntegrationTest {
    @Test
    fun queueRegister_withoutSession_thenSyncAfterLogin_registersPendingToken() =
        runTest {
            val stateStore = InMemoryPushTokenSyncStateStore()
            val sessionStore = InMemorySessionStore(initialLogin = null)
            val remoteDataSource = RecordingRemoteDataSource()
            val orchestrator =
                PushTokenSyncOrchestrator(
                    remoteDataSource = remoteDataSource,
                    stateStore = stateStore,
                    sessionStore = sessionStore,
                    metadataProvider = StaticPushTokenMetadataProvider(),
                    analyticsLogger = RecordingPushAnalyticsLogger(),
                )

            orchestrator.queueRegister(token = "fcm-1", reason = "on_new_token")

            assertEquals(0, remoteDataSource.registerCalls)
            assertEquals(
                PendingPushTokenSync(
                    action = PushTokenSyncAction.Register,
                    token = "fcm-1",
                ),
                stateStore.pending,
            )

            sessionStore.saveSession(login = "moksem", isRegistered = true)
            orchestrator.syncPending(reason = "session_restored")

            assertEquals(1, remoteDataSource.registerCalls)
            assertEquals("fcm-1", remoteDataSource.lastRegisterRequest?.token)
            assertNull(stateStore.pending)
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

    private class InMemorySessionStore(initialLogin: String?) : SessionStore {
        private val state =
            MutableStateFlow(
                initialLogin?.let { StoredSession(login = it, isRegistered = true, updatedAtEpochMs = 1L) },
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
                    updatedAtEpochMs = 2L,
                )
        }

        override suspend fun clearSession() {
            state.value = null
        }
    }

    private class RecordingRemoteDataSource : DevPulseRemoteDataSource {
        var registerCalls: Int = 0
        var lastRegisterRequest: DeviceTokenRequestDto? = null

        override suspend fun registerDeviceToken(request: DeviceTokenRequestDto): RemoteCallResult<Unit> {
            registerCalls += 1
            lastRegisterRequest = request
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterDeviceToken(request: PushTokenDeactivateRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
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
        override fun getMetadata(): PushTokenMetadata = PushTokenMetadata(appVersion = "1.75.0", deviceId = "device-1")
    }

    private class RecordingPushAnalyticsLogger : PushAnalyticsTracker {
        override fun tokenRegistered(
            statusCode: Int,
            reason: String,
            source: String,
        ) = Unit

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
