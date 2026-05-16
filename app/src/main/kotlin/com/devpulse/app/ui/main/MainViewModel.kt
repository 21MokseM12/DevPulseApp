package com.devpulse.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.data.local.preferences.AppThemeMode
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.ThemePreferenceStore
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.ui.auth.AuthSuccessEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartupDestination {
    Loading,
    Auth,
    Subscriptions,
}

data class MainUiState(
    val environment: String = "",
    val baseUrl: String = "",
    val isBootstrapping: Boolean = true,
    val hasCachedSession: Boolean = false,
    val startupDestination: StartupDestination = StartupDestination.Loading,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val appBootstrapRepository: AppBootstrapRepository,
        private val sessionStore: SessionStore,
        private val accountLifecycleUseCase: AccountLifecycleUseCase,
        private val themePreferenceStore: ThemePreferenceStore = DefaultThemePreferenceStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                themePreferenceStore.observeThemeMode().collectLatest { mode ->
                    _uiState.update { state -> state.copy(appThemeMode = mode) }
                }
            }
            viewModelScope.launch {
                val bootstrap = appBootstrapRepository.loadBootstrapInfo()
                _uiState.update { state ->
                    state.copy(
                        environment = bootstrap.environment,
                        baseUrl = bootstrap.baseUrl,
                        isBootstrapping = false,
                        startupDestination =
                            resolveStartupDestination(
                                isBootstrapping = false,
                                hasCachedSession = state.hasCachedSession,
                            ),
                    )
                }
            }
            viewModelScope.launch {
                sessionStore.observeSession().collectLatest { session ->
                    _uiState.update { state ->
                        val hasSession = session != null
                        state.copy(
                            hasCachedSession = hasSession,
                            startupDestination =
                                resolveStartupDestination(
                                    isBootstrapping = state.isBootstrapping,
                                    hasCachedSession = hasSession,
                                ),
                        )
                    }
                }
            }
        }

        fun onAuthSucceeded(event: AuthSuccessEvent) {
            viewModelScope.launch {
                sessionStore.saveSession(
                    login = event.login,
                    isRegistered = true,
                )
            }
        }

        fun onLogout() {
            viewModelScope.launch {
                accountLifecycleUseCase.logout()
            }
        }

        private fun resolveStartupDestination(
            isBootstrapping: Boolean,
            hasCachedSession: Boolean,
        ): StartupDestination {
            if (isBootstrapping) {
                return StartupDestination.Loading
            }
            return if (hasCachedSession) {
                StartupDestination.Subscriptions
            } else {
                StartupDestination.Auth
            }
        }

        private companion object {
            val DefaultThemePreferenceStore: ThemePreferenceStore =
                object : ThemePreferenceStore {
                    override fun observeThemeMode() = flowOf(AppThemeMode.SYSTEM)

                    override suspend fun setThemeMode(mode: AppThemeMode) = Unit
                }
        }
    }
