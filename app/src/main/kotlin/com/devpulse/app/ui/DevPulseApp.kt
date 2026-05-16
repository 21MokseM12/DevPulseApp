package com.devpulse.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.OpenUpdatesDigestContextRequest
import com.devpulse.app.data.local.preferences.AppThemeMode
import com.devpulse.app.ui.main.MainViewModel
import com.devpulse.app.ui.navigation.AppNavGraph
import com.devpulse.app.ui.theme.DevPulseTheme

@Composable
fun DevPulseApp(
    openUpdatesRequest: Boolean = false,
    openUpdatesDigestContextRequest: OpenUpdatesDigestContextRequest? = null,
    onOpenUpdatesHandled: () -> Unit = {},
    onOpenUpdatesDigestContextHandled: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val darkTheme =
        when (uiState.appThemeMode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    DevPulseTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavGraph(
                uiState = uiState,
                onLoginClick = viewModel::onAuthSucceeded,
                onLogoutClick = viewModel::onLogout,
                openUpdatesRequest = openUpdatesRequest,
                openUpdatesDigestContextRequest = openUpdatesDigestContextRequest,
                onOpenUpdatesHandled = onOpenUpdatesHandled,
                onOpenUpdatesDigestContextHandled = onOpenUpdatesDigestContextHandled,
            )
        }
    }
}
