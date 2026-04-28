package com.devpulse.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.repository.AppBootstrapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val environment: String = "",
    val baseUrl: String = "",
    val isBootstrapping: Boolean = true,
    val hasCachedSession: Boolean = false,
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val appBootstrapRepository: AppBootstrapRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val bootstrap = appBootstrapRepository.loadBootstrapInfo()
                _uiState.update { state ->
                    state.copy(
                        environment = bootstrap.environment,
                        baseUrl = bootstrap.baseUrl,
                        isBootstrapping = false,
                        hasCachedSession = bootstrap.hasCachedSession,
                    )
                }
            }
        }

        fun onLoginSucceeded() {
            _uiState.update { state ->
                state.copy(hasCachedSession = true)
            }
        }

        fun onLogout() {
            _uiState.update { state ->
                state.copy(hasCachedSession = false)
            }
        }
    }
