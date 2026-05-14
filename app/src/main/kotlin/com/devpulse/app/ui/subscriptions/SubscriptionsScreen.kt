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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.ui.testing.SmokeTestTags
import java.text.DateFormat
import java.util.Date

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
        onAddLinkInputChange = viewModel::onAddLinkInputChanged,
        onAddTagsInputChange = viewModel::onAddTagsInputChanged,
        onAddFiltersInputChange = viewModel::onAddFiltersInputChanged,
        onAddSubscription = viewModel::addSubscription,
        onRemoveRequested = viewModel::onRemoveRequested,
        onRemoveDismissed = viewModel::onRemoveDismissed,
        onRemoveConfirmed = viewModel::confirmRemove,
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
    onAddLinkInputChange: (String) -> Unit,
    onAddTagsInputChange: (String) -> Unit,
    onAddFiltersInputChange: (String) -> Unit,
    onAddSubscription: () -> Unit,
    onRemoveRequested: (TrackedLink) -> Unit,
    onRemoveDismissed: () -> Unit,
    onRemoveConfirmed: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = "Subscriptions",
            style = MaterialTheme.typography.headlineMedium,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_TITLE),
        )
        TopActions(
            onGoToUpdates = onGoToUpdates,
            onGoToSettings = onGoToSettings,
            onLogout = onLogout,
        )
        AddSubscriptionForm(
            uiState = uiState,
            onAddLinkInputChange = onAddLinkInputChange,
            onAddTagsInputChange = onAddTagsInputChange,
            onAddFiltersInputChange = onAddFiltersInputChange,
            onAddSubscription = onAddSubscription,
        )
        if (uiState.removeErrorMessage != null) {
            Text(
                text = uiState.removeErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (uiState.isStaleData) {
            Text(
                text = staleDataMessage(uiState.lastSyncAtEpochMs),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

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
                    LinksContent(
                        links = uiState.links,
                        isRemoving = uiState.isRemoving,
                        onRemoveRequested = onRemoveRequested,
                    )
                }
            }
        }
    }

    if (uiState.pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = onRemoveDismissed,
            title = { Text(text = "Удалить подписку?") },
            text = { Text(text = uiState.pendingRemoval.url) },
            confirmButton = {
                TextButton(
                    onClick = onRemoveConfirmed,
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON),
                ) {
                    Text(text = "Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = onRemoveDismissed) {
                    Text(text = "Отмена")
                }
            },
        )
    }
}

private fun staleDataMessage(lastSyncAtEpochMs: Long?): String {
    if (lastSyncAtEpochMs == null) {
        return "Показаны оффлайн-данные. Синхронизация еще не выполнялась."
    }
    val formattedDate =
        DateFormat
            .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(lastSyncAtEpochMs))
    return "Показаны оффлайн-данные. Последняя синхронизация: $formattedDate"
}

@Composable
private fun AddSubscriptionForm(
    uiState: SubscriptionsUiState,
    onAddLinkInputChange: (String) -> Unit,
    onAddTagsInputChange: (String) -> Unit,
    onAddFiltersInputChange: (String) -> Unit,
    onAddSubscription: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = uiState.addLinkInput,
            onValueChange = onAddLinkInputChange,
            label = { Text(text = "URL") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_LINK_INPUT),
            enabled = !uiState.isAdding,
        )
        OutlinedTextField(
            value = uiState.addTagsInput,
            onValueChange = onAddTagsInputChange,
            label = { Text(text = "Tags (через запятую)") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_TAGS_INPUT),
            enabled = !uiState.isAdding,
        )
        OutlinedTextField(
            value = uiState.addFiltersInput,
            onValueChange = onAddFiltersInputChange,
            label = { Text(text = "Filters (через запятую)") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_FILTERS_INPUT),
            enabled = !uiState.isAdding,
        )
        if (uiState.addErrorMessage != null) {
            Text(
                text = uiState.addErrorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = onAddSubscription,
            enabled = !uiState.isAdding,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_ADD_BUTTON),
        ) {
            if (uiState.isAdding) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            } else {
                Text(text = "Добавить ссылку")
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
        Button(
            onClick = onGoToUpdates,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_OPEN_UPDATES_BUTTON),
        ) { Text(text = "Открыть Updates") }
        Button(onClick = onGoToSettings, modifier = Modifier.weight(1f)) { Text(text = "Открыть Settings") }
        Button(
            onClick = onLogout,
            modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_LOGOUT_BUTTON),
        ) { Text(text = "Выйти") }
    }
}

@Composable
private fun LinksContent(
    links: List<TrackedLink>,
    isRemoving: Boolean,
    onRemoveRequested: (TrackedLink) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = links, key = { it.id }) { link ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SmokeTestTags.subscriptionRow(link.id)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onRemoveRequested(link) },
                    enabled = !isRemoving,
                    modifier = Modifier.testTag(SmokeTestTags.subscriptionRemoveButton(link.id)),
                ) {
                    Text(text = "Удалить")
                }
            }
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
