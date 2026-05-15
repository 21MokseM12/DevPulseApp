package com.devpulse.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.repository.AuthResult
import com.devpulse.app.domain.usecase.LoginClientUseCase
import com.devpulse.app.domain.usecase.RegisterClientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val login: String = "",
    val password: String = "",
    val isLoginLoading: Boolean = false,
    val isRegisterLoading: Boolean = false,
    val loginErrorMessage: String? = null,
    val registerErrorMessage: String? = null,
    val lastSubmittedAction: AuthAction? = null,
    val pendingAuthSuccess: AuthSuccessEvent? = null,
    val loginButtonState: AuthButtonUiState = AuthButtonUiState.create(AuthAction.Login, AuthButtonStatus.Idle),
    val registerButtonState: AuthButtonUiState = AuthButtonUiState.create(AuthAction.Register, AuthButtonStatus.Idle),
) {
    val isLoading: Boolean
        get() = isLoginLoading || isRegisterLoading

    val activeErrorMessage: String?
        get() =
            when (lastSubmittedAction) {
                AuthAction.Login -> loginErrorMessage
                AuthAction.Register -> registerErrorMessage
                null -> null
            }
}

data class AuthSuccessEvent(
    val login: String,
    val action: AuthAction,
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
        private val loginClientUseCase: LoginClientUseCase,
        private val registerClientUseCase: RegisterClientUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AuthUiState())
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
        private var activeAuthJob: Job? = null

        fun onLoginChanged(value: String) {
            _uiState.update { state ->
                if (state.isLoading) {
                    return@update state
                }
                state.copy(
                    login = value,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                ).resetButtonsToIdle()
            }
        }

        fun onPasswordChanged(value: String) {
            _uiState.update { state ->
                if (state.isLoading) {
                    return@update state
                }
                state.copy(
                    password = value,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                ).resetButtonsToIdle()
            }
        }

        fun submitLogin() {
            val current = _uiState.value
            if (current.isLoading) return

            val login = current.login.trim()
            val password = current.password.trim()
            if (login.isBlank() || password.isBlank()) {
                _uiState.update { state ->
                    state.copy(
                        lastSubmittedAction = AuthAction.Login,
                        loginErrorMessage = validationMessage(AuthAction.Login),
                    ).setButtonStates(
                        action = AuthAction.Login,
                        status = AuthButtonStatus.Error,
                    )
                }
                return
            }
            _uiState.update { state ->
                state.copy(
                    isLoginLoading = true,
                    isRegisterLoading = false,
                    lastSubmittedAction = AuthAction.Login,
                    loginErrorMessage = null,
                ).setButtonStates(
                    action = AuthAction.Login,
                    status = AuthButtonStatus.Loading,
                )
            }

            activeAuthJob =
                viewModelScope.launch {
                    try {
                        when (
                            val result =
                                loginClientUseCase(
                                    login = login,
                                    password = password,
                                )
                        ) {
                            is AuthResult.Success -> {
                                _uiState.update { state ->
                                    state.copy(
                                        login = login,
                                        password = "",
                                        isLoginLoading = false,
                                        isRegisterLoading = false,
                                        loginErrorMessage = null,
                                        registerErrorMessage = null,
                                        pendingAuthSuccess =
                                            AuthSuccessEvent(
                                                login = login,
                                                action = AuthAction.Login,
                                            ),
                                    ).setButtonStates(
                                        action = AuthAction.Login,
                                        status = AuthButtonStatus.Success,
                                    )
                                }
                            }

                            is AuthResult.Failure -> {
                                _uiState.update { state ->
                                    state.copy(
                                        isLoginLoading = false,
                                        isRegisterLoading = false,
                                        loginErrorMessage = failureMessage(AuthAction.Login, result.error),
                                    ).setButtonStates(
                                        action = AuthAction.Login,
                                        status = AuthButtonStatus.Error,
                                    )
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        // Запрос был отменен при уходе со страницы auth.
                    } finally {
                        activeAuthJob = null
                    }
                }
        }

        fun submitRegister() {
            val current = _uiState.value
            if (current.isLoading) return

            val login = current.login.trim()
            val password = current.password.trim()
            if (login.isBlank() || password.isBlank()) {
                _uiState.update { state ->
                    state.copy(
                        lastSubmittedAction = AuthAction.Register,
                        registerErrorMessage = validationMessage(AuthAction.Register),
                    ).setButtonStates(
                        action = AuthAction.Register,
                        status = AuthButtonStatus.Error,
                    )
                }
                return
            }
            _uiState.update { state ->
                state.copy(
                    isLoginLoading = false,
                    isRegisterLoading = true,
                    lastSubmittedAction = AuthAction.Register,
                    registerErrorMessage = null,
                ).setButtonStates(
                    action = AuthAction.Register,
                    status = AuthButtonStatus.Loading,
                )
            }

            activeAuthJob =
                viewModelScope.launch {
                    try {
                        when (
                            val result =
                                registerClientUseCase(
                                    login = login,
                                    password = password,
                                )
                        ) {
                            is AuthResult.Success -> {
                                _uiState.update { state ->
                                    state.copy(
                                        login = login,
                                        password = "",
                                        isLoginLoading = false,
                                        isRegisterLoading = false,
                                        loginErrorMessage = null,
                                        registerErrorMessage = null,
                                        pendingAuthSuccess =
                                            AuthSuccessEvent(
                                                login = login,
                                                action = AuthAction.Register,
                                            ),
                                    ).setButtonStates(
                                        action = AuthAction.Register,
                                        status = AuthButtonStatus.Success,
                                    )
                                }
                            }

                            is AuthResult.Failure -> {
                                _uiState.update { state ->
                                    state.copy(
                                        isLoginLoading = false,
                                        isRegisterLoading = false,
                                        registerErrorMessage =
                                            failureMessage(AuthAction.Register, result.error),
                                    ).setButtonStates(
                                        action = AuthAction.Register,
                                        status = AuthButtonStatus.Error,
                                    )
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        // Запрос был отменен при уходе со страницы auth.
                    } finally {
                        activeAuthJob = null
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
            error: ApiError,
        ): String {
            return AuthErrorMessageMapper.map(action = action, error = error)
        }

        fun onAuthSuccessHandled() {
            _uiState.update { state ->
                state.copy(
                    pendingAuthSuccess = null,
                    password = "",
                    isLoginLoading = false,
                    isRegisterLoading = false,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                    lastSubmittedAction = null,
                ).resetButtonsToIdle()
            }
        }

        fun cancelPendingAuthRequest() {
            activeAuthJob?.cancel()
            activeAuthJob = null
            _uiState.update { state ->
                if (!state.isLoading) {
                    state
                } else {
                    state.copy(
                        isLoginLoading = false,
                        isRegisterLoading = false,
                        loginErrorMessage = null,
                        registerErrorMessage = null,
                        lastSubmittedAction = null,
                    ).resetButtonsToIdle()
                }
            }
        }

        override fun onCleared() {
            cancelPendingAuthRequest()
            super.onCleared()
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
