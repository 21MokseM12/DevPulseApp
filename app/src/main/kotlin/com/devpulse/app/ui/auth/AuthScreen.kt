package com.devpulse.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.ui.testing.SmokeTestTags

@Composable
fun AuthRoute(
    onAuthorized: (AuthSuccessEvent) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(uiState.pendingAuthSuccess) {
        val successEvent = uiState.pendingAuthSuccess ?: return@LaunchedEffect
        onAuthorized(successEvent)
        viewModel.onAuthSuccessHandled()
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelPendingAuthRequest()
        }
    }

    AuthScreen(
        uiState = uiState,
        onLoginChange = viewModel::onLoginChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onLoginSubmit = viewModel::submitLogin,
        onRegisterSubmit = viewModel::submitRegister,
    )
}

@Composable
private fun AuthScreen(
    uiState: AuthUiState,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginSubmit: () -> Unit,
    onRegisterSubmit: () -> Unit,
) {
    val isLoginLoading = uiState.isLoginLoading
    val isRegisterLoading = uiState.isRegisterLoading

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Auth",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(SmokeTestTags.AUTH_TITLE),
        )
        OutlinedTextField(
            value = uiState.login,
            onValueChange = onLoginChange,
            label = { Text(text = "Логин") },
            singleLine = true,
            isError = uiState.loginInlineError != null,
            supportingText = {
                val message = uiState.loginInlineError
                if (message != null) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            },
            enabled = !uiState.isLoading,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_LOGIN_INPUT),
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(text = "Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = uiState.passwordInlineError != null,
            supportingText = {
                val message = uiState.passwordInlineError
                if (message != null) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            },
            enabled = !uiState.isLoading,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_PASSWORD_INPUT),
        )
        val activeErrorMessage = uiState.activeErrorMessage
        if (activeErrorMessage != null) {
            Text(
                text = activeErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onLoginSubmit,
            enabled = !uiState.isLoading && uiState.isCredentialsValid,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_LOGIN_BUTTON),
        ) {
            if (isLoginLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier =
                            Modifier
                                .padding(vertical = 2.dp)
                                .testTag(SmokeTestTags.AUTH_LOGIN_LOADER),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = uiState.loginButtonState.text)
                }
            } else {
                Text(text = uiState.loginButtonState.text)
            }
        }
        OutlinedButton(
            onClick = onRegisterSubmit,
            enabled = !uiState.isLoading && uiState.isCredentialsValid,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_REGISTER_BUTTON),
        ) {
            if (isRegisterLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier =
                            Modifier
                                .padding(vertical = 2.dp)
                                .testTag(SmokeTestTags.AUTH_REGISTER_LOADER),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = uiState.registerButtonState.text)
                }
            } else {
                Text(text = uiState.registerButtonState.text)
            }
        }
    }
}
