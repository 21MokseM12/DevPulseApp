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
    val loginButtonState: AuthButtonUiState = AuthButtonUiState.create(AuthAction.Login, AuthButtonStatus.Idle),
    val registerButtonState: AuthButtonUiState = AuthButtonUiState.create(AuthAction.Register, AuthButtonStatus.Idle),
)

enum class AuthAction {
    Login,
    Register,
}

enum class AuthButtonStatus {
    Idle,
    Loading,
    Error,
    Success,
}

data class AuthButtonUiState(
    val action: AuthAction,
    val status: AuthButtonStatus,
    val text: String,
) {
    companion object {
        fun create(
            action: AuthAction,
            status: AuthButtonStatus,
        ): AuthButtonUiState {
            return AuthButtonUiState(
                action = action,
                status = status,
                text = AuthButtonTextContract.resolve(action, status),
            )
        }
    }
}

object AuthButtonTextContract {
    private val loginStates: Map<AuthButtonStatus, String> =
        mapOf(
            AuthButtonStatus.Idle to "Войти",
            AuthButtonStatus.Loading to "Входим...",
            AuthButtonStatus.Error to "Повторить вход",
            AuthButtonStatus.Success to "Вход выполнен",
        )
    private val registerStates: Map<AuthButtonStatus, String> =
        mapOf(
            AuthButtonStatus.Idle to "Зарегистрироваться",
            AuthButtonStatus.Loading to "Регистрируем...",
            AuthButtonStatus.Error to "Повторить регистрацию",
            AuthButtonStatus.Success to "Регистрация выполнена",
        )

    fun resolve(
        action: AuthAction,
        status: AuthButtonStatus,
    ): String {
        val states =
            when (action) {
                AuthAction.Login -> loginStates
                AuthAction.Register -> registerStates
            }
        return requireNotNull(states[status]) { "Missing text contract for $action/$status" }
    }
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
                state.copy(login = value, errorMessage = null).resetButtonsToIdle()
            }
        }

        fun onPasswordChanged(value: String) {
            _uiState.update { state ->
                state.copy(password = value, errorMessage = null).resetButtonsToIdle()
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
                        errorMessage = validationMessage(action),
                    ).setButtonStates(
                        action = action,
                        status = AuthButtonStatus.Error,
                    )
                }
                return
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    loadingAction = action,
                    errorMessage = null,
                ).setButtonStates(
                    action = action,
                    status = AuthButtonStatus.Loading,
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
                            ).setButtonStates(
                                action = action,
                                status = AuthButtonStatus.Success,
                            )
                        }
                    }

                    is RemoteCallResult.ApiFailure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingAction = null,
                                errorMessage = failureMessage(action, result.error.userMessage),
                            ).setButtonStates(
                                action = action,
                                status = AuthButtonStatus.Error,
                            )
                        }
                    }

                    is RemoteCallResult.NetworkFailure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                loadingAction = null,
                                errorMessage = failureMessage(action, result.error.userMessage),
                            ).setButtonStates(
                                action = action,
                                status = AuthButtonStatus.Error,
                            )
                        }
                    }
                }
            }
        }

        private fun validationMessage(action: AuthAction): String {
            return when (action) {
                AuthAction.Login -> "Для входа заполните логин и пароль."
                AuthAction.Register -> "Для регистрации заполните логин и пароль."
            }
        }

        private fun failureMessage(
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
                ).resetButtonsToIdle()
            }
        }

        private fun AuthUiState.resetButtonsToIdle(): AuthUiState {
            return copy(
                loginButtonState = AuthButtonUiState.create(AuthAction.Login, AuthButtonStatus.Idle),
                registerButtonState = AuthButtonUiState.create(AuthAction.Register, AuthButtonStatus.Idle),
            )
        }

        private fun AuthUiState.setButtonStates(
            action: AuthAction,
            status: AuthButtonStatus,
        ): AuthUiState {
            return when (action) {
                AuthAction.Login ->
                    copy(
                        loginButtonState = AuthButtonUiState.create(AuthAction.Login, status),
                        registerButtonState = AuthButtonUiState.create(AuthAction.Register, AuthButtonStatus.Idle),
                    )

                AuthAction.Register ->
                    copy(
                        loginButtonState = AuthButtonUiState.create(AuthAction.Login, AuthButtonStatus.Idle),
                        registerButtonState = AuthButtonUiState.create(AuthAction.Register, status),
                    )
            }
        }
    }
