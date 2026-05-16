package com.devpulse.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.data.local.preferences.AppThemeMode
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import com.devpulse.app.data.local.preferences.ThemePreferenceStore
import com.devpulse.app.domain.usecase.AccountLifecycleResult
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.push.DigestScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

enum class LogoutActionStatus {
    Idle,
    InProgress,
}

enum class UnregisterActionStatus {
    Idle,
    InProgress,
}

data class SettingsUiState(
    val hasRequestedNotificationPermission: Boolean = false,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val logoutStatus: LogoutActionStatus = LogoutActionStatus.Idle,
    val unregisterStatus: UnregisterActionStatus = UnregisterActionStatus.Idle,
    val showUnregisterConfirmation: Boolean = false,
    val unregisterErrorMessage: String? = null,
    val shouldNavigateToAuth: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val notificationPermissionStore: NotificationPermissionStore,
        private val notificationPreferencesStore: NotificationPreferencesStore,
        private val accountLifecycleUseCase: AccountLifecycleUseCase,
        private val digestScheduler: DigestScheduler,
        private val themePreferenceStore: ThemePreferenceStore = InMemoryThemePreferenceStore(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                notificationPermissionStore.observeHasRequested().collect { hasRequested ->
                    _uiState.update { state ->
                        state.copy(hasRequestedNotificationPermission = hasRequested)
                    }
                }
            }
            viewModelScope.launch {
                notificationPreferencesStore.observePreferences().collect { preferences ->
                    _uiState.update { state ->
                        state.copy(notificationPreferences = preferences)
                    }
                }
            }
            viewModelScope.launch {
                themePreferenceStore.observeThemeMode().collect { mode ->
                    _uiState.update { state -> state.copy(appThemeMode = mode) }
                }
            }
        }

        fun onThemeModeSelected(mode: AppThemeMode) {
            viewModelScope.launch {
                themePreferenceStore.setThemeMode(mode)
            }
        }

        fun onPermissionRequestTriggered() {
            viewModelScope.launch {
                notificationPermissionStore.markRequested()
            }
        }

        fun onNotificationToggleChanged(enabled: Boolean) {
            viewModelScope.launch {
                notificationPreferencesStore.setEnabled(enabled)
                syncDigestScheduler()
            }
        }

        fun onSystemNotificationCapabilityChanged(canPostNotifications: Boolean) {
            if (canPostNotifications) return
            viewModelScope.launch {
                runCatching { notificationPreferencesStore.getPreferences() }
                    .onSuccess { current ->
                        if (current.enabled) {
                            notificationPreferencesStore.setEnabled(false)
                            syncDigestScheduler()
                        }
                    }
            }
        }

        fun onNotificationPresentationModeSelected(mode: NotificationPresentationMode) {
            viewModelScope.launch {
                notificationPreferencesStore.setPresentationMode(mode)
            }
        }

        fun onNotificationDigestModeToggled(enabled: Boolean) {
            viewModelScope.launch {
                val mode = if (enabled) NotificationDigestMode.Daily else null
                notificationPreferencesStore.setDigestMode(mode)
                syncDigestScheduler()
            }
        }

        fun onNotificationDigestModeSelected(mode: NotificationDigestMode) {
            viewModelScope.launch {
                notificationPreferencesStore.setDigestMode(mode)
                syncDigestScheduler()
            }
        }

        fun onQuietHoursEnabledChanged(enabled: Boolean) {
            updateQuietHoursPolicy { it.copy(enabled = enabled) }
        }

        fun onQuietHoursStartShifted(deltaMinutes: Int) {
            updateQuietHoursPolicy { policy ->
                policy.copy(fromMinutes = shiftMinutes(policy.fromMinutes, deltaMinutes))
            }
        }

        fun onQuietHoursEndShifted(deltaMinutes: Int) {
            updateQuietHoursPolicy { policy ->
                policy.copy(toMinutes = shiftMinutes(policy.toMinutes, deltaMinutes))
            }
        }

        fun onQuietHoursWeekdayToggled(day: DayOfWeek) {
            updateQuietHoursPolicy { policy ->
                val nextWeekdays =
                    if (policy.weekdays.contains(day)) {
                        policy.weekdays - day
                    } else {
                        policy.weekdays + day
                    }
                policy.copy(weekdays = if (nextWeekdays.isEmpty()) policy.weekdays else nextWeekdays)
            }
        }

        fun onQuietHoursTimezoneModeSelected(mode: QuietHoursTimezoneMode) {
            updateQuietHoursPolicy { policy ->
                when (mode) {
                    QuietHoursTimezoneMode.Device ->
                        policy.copy(
                            timezoneMode = QuietHoursTimezoneMode.Device,
                            fixedZoneId = null,
                        )
                    QuietHoursTimezoneMode.Fixed ->
                        policy.copy(
                            timezoneMode = QuietHoursTimezoneMode.Fixed,
                            fixedZoneId = policy.resolvedFixedZoneId ?: java.time.ZoneOffset.UTC.id,
                        )
                }
            }
        }

        fun onLogoutRequested() {
            if (_uiState.value.logoutStatus == LogoutActionStatus.InProgress) return

            _uiState.update { state ->
                state.copy(
                    logoutStatus = LogoutActionStatus.InProgress,
                    unregisterErrorMessage = null,
                    shouldNavigateToAuth = false,
                )
            }
            viewModelScope.launch {
                val result = accountLifecycleUseCase.logout()
                _uiState.update { state ->
                    when (result) {
                        AccountLifecycleResult.Success ->
                            state.copy(
                                logoutStatus = LogoutActionStatus.Idle,
                                shouldNavigateToAuth = true,
                            )
                        is AccountLifecycleResult.Failure ->
                            state.copy(
                                logoutStatus = LogoutActionStatus.Idle,
                                unregisterErrorMessage = result.error.userMessage,
                            )
                        AccountLifecycleResult.Cancelled ->
                            state.copy(
                                logoutStatus = LogoutActionStatus.Idle,
                                unregisterErrorMessage = "Операция выхода отменена.",
                            )
                    }
                }
            }
        }

        fun onUnregisterRequested() {
            _uiState.update { state ->
                state.copy(
                    showUnregisterConfirmation = true,
                    unregisterErrorMessage = null,
                    shouldNavigateToAuth = false,
                )
            }
        }

        fun onUnregisterDismissed() {
            _uiState.update { state ->
                state.copy(showUnregisterConfirmation = false)
            }
        }

        fun onUnregisterConfirmed() {
            if (_uiState.value.unregisterStatus == UnregisterActionStatus.InProgress) return
            _uiState.update { state ->
                state.copy(
                    showUnregisterConfirmation = false,
                    unregisterStatus = UnregisterActionStatus.InProgress,
                    unregisterErrorMessage = null,
                )
            }
            viewModelScope.launch {
                val result = accountLifecycleUseCase.unregister()
                _uiState.update { state ->
                    when (result) {
                        AccountLifecycleResult.Success ->
                            state.copy(
                                unregisterStatus = UnregisterActionStatus.Idle,
                                shouldNavigateToAuth = true,
                            )
                        is AccountLifecycleResult.Failure ->
                            state.copy(
                                unregisterStatus = UnregisterActionStatus.Idle,
                                unregisterErrorMessage = result.error.userMessage,
                            )
                        AccountLifecycleResult.Cancelled ->
                            state.copy(
                                unregisterStatus = UnregisterActionStatus.Idle,
                                unregisterErrorMessage = "Удаление аккаунта отменено.",
                            )
                    }
                }
            }
        }

        fun onAuthNavigationHandled() {
            _uiState.update { state ->
                if (!state.shouldNavigateToAuth) {
                    state
                } else {
                    state.copy(shouldNavigateToAuth = false)
                }
            }
        }

        private fun updateQuietHoursPolicy(transform: (QuietHoursPolicy) -> QuietHoursPolicy) {
            viewModelScope.launch {
                runCatching { notificationPreferencesStore.getPreferences() }
                    .onSuccess { preferences ->
                        notificationPreferencesStore.setQuietHoursPolicy(
                            transform(preferences.quietHoursPolicy),
                        )
                    }
            }
        }

        private fun shiftMinutes(
            current: Int,
            delta: Int,
        ): Int {
            val total = 24 * 60
            val next = (current + delta) % total
            return if (next < 0) next + total else next
        }

        private suspend fun syncDigestScheduler() {
            runCatching { notificationPreferencesStore.getPreferences() }
                .onSuccess { preferences -> digestScheduler.sync(preferences) }
        }

        private class InMemoryThemePreferenceStore : ThemePreferenceStore {
            private val modeFlow = MutableStateFlow(AppThemeMode.SYSTEM)

            override fun observeThemeMode(): StateFlow<AppThemeMode> = modeFlow

            override suspend fun setThemeMode(mode: AppThemeMode) {
                modeFlow.value = mode
            }
        }
    }
