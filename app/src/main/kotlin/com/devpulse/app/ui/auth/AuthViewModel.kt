package com.devpulse.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.validation.AuthCredentialsValidationResult
import com.devpulse.app.domain.validation.AuthCredentialsValidator
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
    val isLoginTouched: Boolean = false,
    val isPasswordTouched: Boolean = false,
    val hasValidationAttempt: Boolean = false,
    val credentialsValidation: AuthCredentialsValidationResult = AuthCredentialsValidationResult(),
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

    val isCredentialsValid: Boolean
        get() = credentialsValidation.isValid

    val loginInlineError: String?
        get() = if (isLoginTouched || hasValidationAttempt) credentialsValidation.loginError?.message else null

    val passwordInlineError: String?
        get() = if (isPasswordTouched || hasValidationAttempt) credentialsValidation.passwordError?.message else null

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
        private val credentialsValidator = AuthCredentialsValidator()

        init {
            _uiState.update { state ->
                state.copy(
                    credentialsValidation =
                        credentialsValidator.validate(
                            loginRaw = state.login,
                            passwordRaw = state.password,
                        ),
                )
            }
        }

        fun onLoginChanged(value: String) {
            _uiState.update { state ->
                if (state.isLoading) {
                    return@update state
                }
                state.copy(
                    login = value,
                    isLoginTouched = true,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                    credentialsValidation =
                        credentialsValidator.validate(
                            loginRaw = value,
                            passwordRaw = state.password,
                        ),
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
                    isPasswordTouched = true,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                    credentialsValidation =
                        credentialsValidator.validate(
                            loginRaw = state.login,
                            passwordRaw = value,
                        ),
                ).resetButtonsToIdle()
            }
        }

        fun submitLogin() {
            val current = _uiState.value
            if (current.isLoading) return

            val login = current.login.trim()
            val password = current.password.trim()
            val validationResult = credentialsValidator.validate(login, password)
            if (!validationResult.isValid) {
                _uiState.update { state ->
                    state.copy(
                        hasValidationAttempt = true,
                        lastSubmittedAction = AuthAction.Login,
                        loginErrorMessage = null,
                        registerErrorMessage = null,
                        credentialsValidation = validationResult,
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
                    hasValidationAttempt = true,
                    lastSubmittedAction = AuthAction.Login,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                    credentialsValidation = validationResult,
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
                                        isLoginTouched = false,
                                        isPasswordTouched = false,
                                        hasValidationAttempt = false,
                                        credentialsValidation = credentialsValidator.validate(loginRaw = login, passwordRaw = ""),
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
            val validationResult = credentialsValidator.validate(login, password)
            if (!validationResult.isValid) {
                _uiState.update { state ->
                    state.copy(
                        hasValidationAttempt = true,
                        lastSubmittedAction = AuthAction.Register,
                        loginErrorMessage = null,
                        registerErrorMessage = null,
                        credentialsValidation = validationResult,
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
                    hasValidationAttempt = true,
                    lastSubmittedAction = AuthAction.Register,
                    loginErrorMessage = null,
                    registerErrorMessage = null,
                    credentialsValidation = validationResult,
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
                                        isLoginTouched = false,
                                        isPasswordTouched = false,
                                        hasValidationAttempt = false,
                                        credentialsValidation = credentialsValidator.validate(loginRaw = login, passwordRaw = ""),
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
                    isLoginTouched = false,
                    isPasswordTouched = false,
                    hasValidationAttempt = false,
                    credentialsValidation = credentialsValidator.validate(loginRaw = state.login, passwordRaw = ""),
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
                        hasValidationAttempt = false,
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
