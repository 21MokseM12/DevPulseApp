package com.devpulse.app.ui.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.domain.model.TrackedLink

@Composable
fun SubscriptionsRoute(
    onGoToUpdates: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    SubscriptionsScreen(
        uiState = uiState,
        onGoToUpdates = onGoToUpdates,
        onGoToSettings = onGoToSettings,
        onLogout = onLogout,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionsScreen(
    uiState: SubscriptionsUiState,
    onGoToUpdates: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = "Subscriptions",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        TopActions(
            onGoToUpdates = onGoToUpdates,
            onGoToSettings = onGoToSettings,
            onLogout = onLogout,
        )

        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(text = "Загружаю подписки...", modifier = Modifier.padding(top = 12.dp))
            }
            return
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                    )
                }

                uiState.links.isEmpty() -> {
                    EmptyState()
                }

                else -> {
                    LinksContent(links = uiState.links)
                }
            }
        }
    }
}

@Composable
private fun TopActions(
    onGoToUpdates: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onGoToUpdates, modifier = Modifier.weight(1f)) { Text(text = "Открыть Updates") }
        Button(onClick = onGoToSettings, modifier = Modifier.weight(1f)) { Text(text = "Открыть Settings") }
        Button(onClick = onLogout) { Text(text = "Выйти") }
    }
}

@Composable
private fun LinksContent(links: List<TrackedLink>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = links, key = { it.id }) { link ->
            Text(
                text = link.url,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "tags: ${link.tags.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "filters: ${link.filters.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Список подписок пуст")
        Text(text = "Потяните вниз, чтобы обновить")
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(text = "Повторить")
        }
    }
}
