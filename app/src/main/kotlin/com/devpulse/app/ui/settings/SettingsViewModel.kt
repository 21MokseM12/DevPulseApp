package com.devpulse.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.domain.usecase.AccountLifecycleResult
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        }

        fun onPermissionRequestTriggered() {
            viewModelScope.launch {
                notificationPermissionStore.markRequested()
            }
        }

        fun onNotificationToggleChanged(enabled: Boolean) {
            viewModelScope.launch {
                notificationPreferencesStore.setEnabled(enabled)
            }
        }

        fun onSystemNotificationCapabilityChanged(canPostNotifications: Boolean) {
            if (canPostNotifications) return
            viewModelScope.launch {
                runCatching { notificationPreferencesStore.getPreferences() }
                    .onSuccess { current ->
                        if (current.enabled) {
                            notificationPreferencesStore.setEnabled(false)
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
    }
