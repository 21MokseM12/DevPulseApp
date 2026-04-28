package com.devpulse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.ui.main.MainViewModel
import com.devpulse.app.ui.theme.DevPulseTheme

@Composable
fun DevPulseApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DevPulseTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (uiState.environment.isNotBlank()) {
                    Text(text = "Environment: ${uiState.environment}")
                    Text(text = "BASE_URL: ${uiState.baseUrl}")
                    Text(text = "Cached session: ${uiState.hasCachedSession}")
                }
            }
        }
    }
}
