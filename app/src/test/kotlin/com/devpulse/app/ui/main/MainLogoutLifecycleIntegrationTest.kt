package com.devpulse.app.ui.main

import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
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
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.push.ParsedPushUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainLogoutLifecycleIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun logout_fromMainFlow_usesLifecycleCleanupAndRoutesToAuth() =
        runTest {
            val steps = mutableListOf<String>()
            val sessionStore = RecordingSessionStore(steps)
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = FakeRemoteDataSource(),
                    sessionStore = sessionStore,
                    updatesRepository = RecordingUpdatesRepository(steps),
                    pushTokenStore = RecordingPushTokenStore(steps),
                    notificationPermissionStore = RecordingNotificationPermissionStore(steps),
                )
            val viewModel =
                MainViewModel(
                    appBootstrapRepository =
                        FakeAppBootstrapRepository(
                            AppBootstrapInfo(
                                environment = "debug",
                                baseUrl = "https://api.example.com/",
                                hasCachedSession = true,
                            ),
                        ),
                    sessionStore = sessionStore,
                    accountLifecycleUseCase = useCase,
                )
            advanceUntilIdle()

            viewModel.onLogout()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)
            assertEquals(
                listOf(
                    "session.clear",
                    "updates.clear",
                    "push.clear",
                    "permission.clear",
                ),
                steps,
            )
        }

    private class FakeAppBootstrapRepository(
        private val info: AppBootstrapInfo,
    ) : AppBootstrapRepository {
        override suspend fun loadBootstrapInfo(): AppBootstrapInfo = info
    }

    private class RecordingSessionStore(
        private val steps: MutableList<String>,
    ) : SessionStore {
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
            steps += "session.clear"
            state.value = null
        }
    }

    private class RecordingUpdatesRepository(
        private val steps: MutableList<String>,
    ) : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = MutableStateFlow(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() {
            steps += "updates.clear"
        }
    }

    private class RecordingPushTokenStore(
        private val steps: MutableList<String>,
    ) : PushTokenStore {
        override fun observeToken(): Flow<String?> = MutableStateFlow("token")

        override suspend fun getToken(): String? = "token"

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearToken() {
            steps += "push.clear"
        }
    }

    private class RecordingNotificationPermissionStore(
        private val steps: MutableList<String>,
    ) : NotificationPermissionStore {
        override fun observeHasRequested(): Flow<Boolean> = MutableStateFlow(true)

        override suspend fun hasRequested(): Boolean = true

        override suspend fun markRequested() = Unit

        override suspend fun clearRequestedFlag() {
            steps += "permission.clear"
        }
    }

    private class FakeRemoteDataSource : DevPulseRemoteDataSource {
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
            error("Not used in this test")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("Not used in this test")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            error("Not used in this test")
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            error("Not used in this test")
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            error("Not used in this test")
        }
    }
}
