package com.devpulse.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val hasRequestedNotificationPermission: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val notificationPermissionStore: NotificationPermissionStore,
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
        }

        fun onPermissionRequestTriggered() {
            viewModelScope.launch {
                notificationPermissionStore.markRequested()
            }
        }
    }
