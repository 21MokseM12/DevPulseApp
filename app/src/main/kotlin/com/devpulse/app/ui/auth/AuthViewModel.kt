package com.devpulse.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val login: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loadingAction: AuthAction? = null,
    val errorMessage: String? = null,
    val isAuthorized: Boolean = false,
)

enum class AuthAction {
    Login,
    Register,
}

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        fun onLoginChanged(value: String) {
            _uiState.update { state ->
                state.copy(login = value, errorMessage = null)
            }
        }

        fun onPasswordChanged(value: String) {
            _uiState.update { state ->
                state.copy(password = value, errorMessage = null)
            }
        }

        fun submitLogin() {
            submit(action = AuthAction.Login)
        }

        fun submitRegister() {
            submit(action = AuthAction.Register)
        }

        fun submit() {
            submitLogin()
        }

        private fun submit(action: AuthAction) {
            val current = _uiState.value
            if (current.isLoading) return

            val login = current.login.trim()
            val password = current.password.trim()
            if (login.isBlank() || password.isBlank()) {
                _uiState.update { state ->
                    state.copy(
                        errorMessage =
                            when (action) {
                                AuthAction.Login -> "Для входа заполните логин и пароль."
                                AuthAction.Register -> "Для регистрации заполните логин и пароль."
                            },
                    )
                }
                return
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    loadingAction = action,
                    errorMessage = null,
                )
            }

            viewModelScope.launch {
                when (
                    val result =
                        remoteDataSource.registerClient(
                            ClientCredentialsRequestDto(
                                login = login,
                                password = password,
                            ),
                        )
                ) {
                    is RemoteCallResult.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                login = login,
                                password = "",
                                isLoading = false,
                                loadingAction = null,
                                isAuthorized = true,
                            )
                        }
                    }

                    is RemoteCallResult.ApiFailure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingAction = null,
                                errorMessage = actionFailureMessage(action, result.error.userMessage),
                            )
                        }
                    }

                    is RemoteCallResult.NetworkFailure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingAction = null,
                                errorMessage = actionFailureMessage(action, result.error.userMessage),
                            )
                        }
                    }
                }
            }
        }

        private fun actionFailureMessage(
            action: AuthAction,
            message: String,
        ): String {
            return when (action) {
                AuthAction.Login -> "Не удалось войти. $message"
                AuthAction.Register -> "Не удалось зарегистрироваться. $message"
            }
        }

        fun onAuthorizationHandled() {
            _uiState.update { state ->
                state.copy(
                    isAuthorized = false,
                    password = "",
                )
            }
        }
    }
