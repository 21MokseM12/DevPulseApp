package com.devpulse.app.ui.settings

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
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
import com.devpulse.app.domain.usecase.AccountLifecycleResult
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.push.DigestScheduler
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onPermissionRequestTriggered_marksPermissionAsRequested() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    FakeNotificationPreferencesStore(),
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasRequestedNotificationPermission)
            viewModel.onPermissionRequestTriggered()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasRequestedNotificationPermission)
        }
    }

    @Test
    fun onUnregisterConfirmed_setsError_whenUseCaseFails() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    FakeNotificationPreferencesStore(),
                    FakeAccountLifecycleUseCase(
                        unregisterResult =
                            AccountLifecycleResult.Failure(
                                ApiError(
                                    kind = ApiErrorKind.NetworkTimeout,
                                    userMessage = "timeout",
                                ),
                            ),
                    ),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onUnregisterRequested()
            viewModel.onUnregisterConfirmed()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showUnregisterConfirmation)
            assertEquals(UnregisterActionStatus.Idle, viewModel.uiState.value.unregisterStatus)
            assertEquals("timeout", viewModel.uiState.value.unregisterErrorMessage)
            assertFalse(viewModel.uiState.value.shouldNavigateToAuth)
        }
    }

    @Test
    fun onLogoutRequested_returnsToIdle_whenOperationCancelled() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    FakeNotificationPreferencesStore(),
                    FakeAccountLifecycleUseCase(
                        logoutResult = AccountLifecycleResult.Cancelled,
                    ),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onLogoutRequested()
            advanceUntilIdle()

            assertEquals(LogoutActionStatus.Idle, viewModel.uiState.value.logoutStatus)
            assertEquals("Операция выхода отменена.", viewModel.uiState.value.unregisterErrorMessage)
            assertFalse(viewModel.uiState.value.shouldNavigateToAuth)
        }
    }

    @Test
    fun onLogoutRequested_success_requestsAuthNavigation_andCanBeConsumed() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    FakeNotificationPreferencesStore(),
                    FakeAccountLifecycleUseCase(
                        logoutResult = AccountLifecycleResult.Success,
                    ),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onLogoutRequested()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.shouldNavigateToAuth)
            viewModel.onAuthNavigationHandled()
            assertFalse(viewModel.uiState.value.shouldNavigateToAuth)
        }
    }

    @Test
    fun onUnregisterConfirmed_success_requestsAuthNavigation_andCanBeConsumed() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    FakeNotificationPreferencesStore(),
                    FakeAccountLifecycleUseCase(
                        unregisterResult = AccountLifecycleResult.Success,
                    ),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onUnregisterRequested()
            viewModel.onUnregisterConfirmed()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.shouldNavigateToAuth)
            viewModel.onAuthNavigationHandled()
            assertFalse(viewModel.uiState.value.shouldNavigateToAuth)
        }
    }

    @Test
    fun onNotificationToggleChanged_updatesUiStateFromPreferencesStore() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val preferencesStore = FakeNotificationPreferencesStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    preferencesStore,
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.notificationPreferences.enabled)
            viewModel.onNotificationToggleChanged(false)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.notificationPreferences.enabled)
        }
    }

    @Test
    fun onNotificationPresentationModeSelected_updatesUiStateFromPreferencesStore() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val preferencesStore = FakeNotificationPreferencesStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    preferencesStore,
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onNotificationPresentationModeSelected(
                NotificationPresentationMode.Compact,
            )
            advanceUntilIdle()

            assertEquals(
                NotificationPresentationMode.Compact,
                viewModel.uiState.value.notificationPreferences.presentationMode,
            )
        }
    }

    @Test
    fun onSystemNotificationCapabilityChanged_whenDenied_forcesDisabledPreference() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val preferencesStore = FakeNotificationPreferencesStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    preferencesStore,
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.notificationPreferences.enabled)
            viewModel.onSystemNotificationCapabilityChanged(canPostNotifications = false)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.notificationPreferences.enabled)
        }
    }

    @Test
    fun onNotificationDigestModeToggled_updatesDigestMode() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val preferencesStore = FakeNotificationPreferencesStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    preferencesStore,
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onNotificationDigestModeToggled(enabled = true)
            advanceUntilIdle()
            assertEquals(NotificationDigestMode.Daily, viewModel.uiState.value.notificationPreferences.digestMode)

            viewModel.onNotificationDigestModeSelected(NotificationDigestMode.EverySixHours)
            advanceUntilIdle()
            assertEquals(
                NotificationDigestMode.EverySixHours,
                viewModel.uiState.value.notificationPreferences.digestMode,
            )

            viewModel.onNotificationDigestModeToggled(enabled = false)
            advanceUntilIdle()
            assertEquals(null, viewModel.uiState.value.notificationPreferences.digestMode)
        }
    }

    @Test
    fun quietHoursHandlers_updateUiStateFromPreferencesStore() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val preferencesStore = FakeNotificationPreferencesStore()
            val viewModel =
                SettingsViewModel(
                    store,
                    preferencesStore,
                    FakeAccountLifecycleUseCase(),
                    FakeDigestScheduler(),
                )
            advanceUntilIdle()

            viewModel.onQuietHoursEnabledChanged(true)
            viewModel.onQuietHoursStartShifted(60)
            viewModel.onQuietHoursEndShifted(-30)
            viewModel.onQuietHoursWeekdayToggled(DayOfWeek.SUNDAY)
            viewModel.onQuietHoursTimezoneModeSelected(QuietHoursTimezoneMode.Fixed)
            advanceUntilIdle()

            val policy = viewModel.uiState.value.notificationPreferences.quietHoursPolicy
            assertTrue(policy.enabled)
            assertEquals(23 * 60, policy.fromMinutes)
            assertEquals(6 * 60 + 30, policy.toMinutes)
            assertFalse(policy.weekdays.contains(DayOfWeek.SUNDAY))
            assertEquals(QuietHoursTimezoneMode.Fixed, policy.timezoneMode)
        }
    }

    private class FakeNotificationPermissionStore : NotificationPermissionStore {
        private val requested = MutableStateFlow(false)

        override fun observeHasRequested(): Flow<Boolean> = requested.asStateFlow()

        override suspend fun hasRequested(): Boolean = requested.value

        override suspend fun markRequested() {
            requested.value = true
        }

        override suspend fun clearRequestedFlag() {
            requested.value = false
        }
    }

    private class FakeAccountLifecycleUseCase(
        private val logoutResult: AccountLifecycleResult = AccountLifecycleResult.Success,
        private val unregisterResult: AccountLifecycleResult = AccountLifecycleResult.Success,
    ) : AccountLifecycleUseCase(
            remoteDataSource = FakeRemoteDataSource(),
            sessionStore = FakeSessionStore(),
            updatesRepository = FakeUpdatesRepository(),
            pushTokenStore = FakePushTokenStore(),
            notificationPermissionStore = FakeNotificationPermissionStore(),
        ) {
        override suspend fun logout(): AccountLifecycleResult = logoutResult

        override suspend fun unregister(): AccountLifecycleResult = unregisterResult
    }

    private class FakeNotificationPreferencesStore : NotificationPreferencesStore {
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

    private class FakeRemoteDataSource : DevPulseRemoteDataSource {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> =
            RemoteCallResult.Success(Unit, 200)

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> =
            RemoteCallResult.Success(Unit, 200)

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> =
            RemoteCallResult.Success(Unit, 200)

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> =
            RemoteCallResult.Success(emptyList(), 200)

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> = error("Not used")

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> =
            error("Not used")

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> =
            RemoteCallResult.Success(
                NotificationListResponseDto(
                    notifications = emptyList(),
                    limit = limit,
                    offset = offset,
                ),
                200,
            )

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> =
            RemoteCallResult.Success(UnreadCountResponseDto(0), 200)

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> =
            RemoteCallResult.Success(MarkReadResponseDto(updatedCount = request.ids?.size ?: 0), 200)
    }

    private class FakeSessionStore : SessionStore {
        override fun observeSession(): Flow<StoredSession?> = emptyFlow()

        override fun observeClientLogin(): Flow<String?> = emptyFlow()

        override suspend fun getSession(): StoredSession? = null

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) = Unit

        override suspend fun clearSession() = Unit
    }

    private class FakeUpdatesRepository : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = emptyFlow()

        override suspend fun saveIncomingUpdate(
            update: com.devpulse.app.push.ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class FakePushTokenStore : PushTokenStore {
        override fun observeToken(): Flow<String?> = emptyFlow()

        override suspend fun getToken(): String? = null

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearToken() = Unit
    }

    private class FakeDigestScheduler : DigestScheduler {
        override fun sync(preferences: NotificationPreferences) = Unit
    }
}
