package com.devpulse.app.ui.settings

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
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
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.push.DigestScheduler
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLifecycleFlowIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun unregister_success_runsLifecycleFlow_networkThenLocalReset() =
        runTest {
            val steps = mutableListOf<String>()
            val remote = RecordingRemoteDataSource(steps, RemoteCallResult.Success(Unit, 200))
            val sessionStore = RecordingSessionStore(steps)
            val updatesRepository = RecordingUpdatesRepository(steps)
            val pushTokenStore = RecordingPushTokenStore(steps)
            val permissionStore = RecordingNotificationPermissionStore(steps)
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = sessionStore,
                    updatesRepository = updatesRepository,
                    pushTokenStore = pushTokenStore,
                    notificationPermissionStore = permissionStore,
                )
            val viewModel =
                SettingsViewModel(
                    permissionStore,
                    RecordingNotificationPreferencesStore(),
                    useCase,
                    RecordingDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onUnregisterRequested()
            assertTrue(viewModel.uiState.value.showUnregisterConfirmation)
            viewModel.onUnregisterConfirmed()
            advanceUntilIdle()

            assertEquals(UnregisterActionStatus.Idle, viewModel.uiState.value.unregisterStatus)
            assertEquals(null, viewModel.uiState.value.unregisterErrorMessage)
            assertFalse(viewModel.uiState.value.showUnregisterConfirmation)
            assertTrue(viewModel.uiState.value.shouldNavigateToAuth)
            assertEquals(
                listOf(
                    "remote.unregister",
                    "session.clear",
                    "updates.clear",
                    "push.clear",
                    "permission.clear",
                ),
                steps,
            )
        }

    @Test
    fun logout_success_resetsStateAndReturnsIdleStatus() =
        runTest {
            val steps = mutableListOf<String>()
            val permissionStore = RecordingNotificationPermissionStore(steps)
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = RecordingRemoteDataSource(steps, RemoteCallResult.Success(Unit, 200)),
                    sessionStore = RecordingSessionStore(steps),
                    updatesRepository = RecordingUpdatesRepository(steps),
                    pushTokenStore = RecordingPushTokenStore(steps),
                    notificationPermissionStore = permissionStore,
                )
            val viewModel =
                SettingsViewModel(
                    permissionStore,
                    RecordingNotificationPreferencesStore(),
                    useCase,
                    RecordingDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onLogoutRequested()
            advanceUntilIdle()

            assertEquals(LogoutActionStatus.Idle, viewModel.uiState.value.logoutStatus)
            assertTrue(viewModel.uiState.value.shouldNavigateToAuth)
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

    @Test
    fun unregister_timeout_keepsUiResponsive_withoutLocalResetOrNavigation() =
        runTest {
            val steps = mutableListOf<String>()
            val remote =
                RecordingRemoteDataSource(
                    steps,
                    RemoteCallResult.NetworkFailure(
                        error = ApiError(ApiErrorKind.NetworkTimeout, "timeout"),
                        throwable = RuntimeException("timeout"),
                    ),
                )
            val permissionStore = RecordingNotificationPermissionStore(steps)
            val useCase =
                AccountLifecycleUseCase(
                    remoteDataSource = remote,
                    sessionStore = RecordingSessionStore(steps),
                    updatesRepository = RecordingUpdatesRepository(steps),
                    pushTokenStore = RecordingPushTokenStore(steps),
                    notificationPermissionStore = permissionStore,
                )
            val viewModel =
                SettingsViewModel(
                    permissionStore,
                    RecordingNotificationPreferencesStore(),
                    useCase,
                    RecordingDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onUnregisterRequested()
            viewModel.onUnregisterConfirmed()
            advanceUntilIdle()

            assertEquals(UnregisterActionStatus.Idle, viewModel.uiState.value.unregisterStatus)
            assertEquals("timeout", viewModel.uiState.value.unregisterErrorMessage)
            assertFalse(viewModel.uiState.value.shouldNavigateToAuth)
            assertEquals(listOf("remote.unregister"), steps)
        }

    private class RecordingRemoteDataSource(
        private val steps: MutableList<String>,
        private val unregisterResult: RemoteCallResult<Unit>,
    ) : DevPulseRemoteDataSource {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            steps += "remote.unregister"
            return unregisterResult
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return RemoteCallResult.Success(emptyList(), 200)
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("Not used")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("Not used")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            error("Not used")
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            error("Not used")
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            error("Not used")
        }
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
        private val requested = MutableStateFlow(false)

        override fun observeHasRequested(): Flow<Boolean> = requested.asStateFlow()

        override suspend fun hasRequested(): Boolean = requested.value

        override suspend fun markRequested() {
            requested.value = true
        }

        override suspend fun clearRequestedFlag() {
            steps += "permission.clear"
            requested.value = false
        }
    }

    private class RecordingNotificationPreferencesStore : NotificationPreferencesStore {
        private val preferences = MutableStateFlow(NotificationPreferences())

        override fun observePreferences(): Flow<NotificationPreferences> = preferences.asStateFlow()

        override suspend fun getPreferences(): NotificationPreferences = preferences.value

        override suspend fun setEnabled(enabled: Boolean) {
            preferences.value = preferences.value.copy(enabled = enabled)
        }

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) {
            preferences.value = preferences.value.copy(presentationMode = mode)
        }

        override suspend fun setDigestMode(mode: NotificationDigestMode?) {
            preferences.value = preferences.value.copy(digestMode = mode)
        }

        override suspend fun setDigestLastProcessedAt(epochMs: Long) {
            preferences.value = preferences.value.copy(digestLastProcessedAtEpochMs = epochMs)
        }

        override suspend fun setQuietHoursPolicy(policy: com.devpulse.app.data.local.preferences.QuietHoursPolicy) {
            preferences.value = preferences.value.copy(quietHoursPolicy = policy)
        }

        override suspend fun reset() {
            preferences.value = NotificationPreferences()
        }
    }

    private class RecordingDigestScheduler : DigestScheduler {
        override fun sync(preferences: NotificationPreferences) = Unit
    }
}
