package com.devpulse.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onAuthorized: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    LaunchedEffect(uiState.isAuthorized) {
        if (uiState.isAuthorized) {
            onAuthorized(uiState.login.trim())
            viewModel.onAuthorizationHandled()
        }
    }

    AuthScreen(
        uiState = uiState,
        onLoginChange = viewModel::onLoginChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onSubmit = viewModel::submit,
    )
}

@Composable
private fun AuthScreen(
    uiState: AuthUiState,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
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
            enabled = !uiState.isLoading,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_PASSWORD_INPUT),
        )
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onSubmit,
            enabled = !uiState.isLoading,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.AUTH_SUBMIT_BUTTON),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            } else {
                Text(text = "Войти")
            }
        }
    }
}
