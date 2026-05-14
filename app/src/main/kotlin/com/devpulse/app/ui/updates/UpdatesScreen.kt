package com.devpulse.app.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.model.UpdatesQuickFilter
import com.devpulse.app.ui.testing.SmokeTestTags

@Composable
fun UpdatesRoute(
    onGoToSubscriptions: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    UpdatesScreen(
        uiState = uiState,
        onGoToSubscriptions = onGoToSubscriptions,
        onGoToSettings = onGoToSettings,
        onLogout = onLogout,
        onMarkAsRead = viewModel::markAsRead,
        onQueryChanged = viewModel::onQueryChanged,
        onUnreadOnlyToggled = viewModel::onUnreadOnlyToggled,
        onSourceChanged = viewModel::onSourceChanged,
        onPeriodChanged = viewModel::onPeriodChanged,
        onTagToggled = viewModel::onTagToggled,
        onQuickFilterSelected = viewModel::applyQuickFilter,
        onResetFilters = viewModel::resetFilters,
    )
}

@Composable
private fun UpdatesScreen(
    uiState: UpdatesUiState,
    onGoToSubscriptions: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onQueryChanged: (String) -> Unit,
    onUnreadOnlyToggled: () -> Unit,
    onSourceChanged: (String?) -> Unit,
    onPeriodChanged: (UpdatesPeriodFilter) -> Unit,
    onTagToggled: (String) -> Unit,
    onQuickFilterSelected: (UpdatesQuickFilter) -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = "Updates",
            style = MaterialTheme.typography.headlineMedium,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(SmokeTestTags.UPDATES_TITLE),
        )
        Text(
            text = "Непрочитанных: ${uiState.unreadCount}",
            style = MaterialTheme.typography.bodyMedium,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(SmokeTestTags.UPDATES_UNREAD_COUNT),
        )
        TopActions(
            onGoToSubscriptions = onGoToSubscriptions,
            onGoToSettings = onGoToSettings,
            onLogout = onLogout,
        )
        FiltersSection(
            uiState = uiState,
            onQueryChanged = onQueryChanged,
            onUnreadOnlyToggled = onUnreadOnlyToggled,
            onSourceChanged = onSourceChanged,
            onPeriodChanged = onPeriodChanged,
            onTagToggled = onTagToggled,
            onQuickFilterSelected = onQuickFilterSelected,
            onResetFilters = onResetFilters,
        )

        if (uiState.actionErrorMessage != null) {
            Text(
                text = uiState.actionErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when {
            uiState.isLoading -> {
                LoadingState()
            }

            uiState.events.isEmpty() -> {
                EmptyState(hasActiveFilters = uiState.filterState.hasActiveFilters)
            }

            else -> {
                UpdatesList(
                    events = uiState.events,
                    markingIds = uiState.markingIds,
                    onMarkAsRead = onMarkAsRead,
                )
            }
        }
    }
}

@Composable
private fun FiltersSection(
    uiState: UpdatesUiState,
    onQueryChanged: (String) -> Unit,
    onUnreadOnlyToggled: () -> Unit,
    onSourceChanged: (String?) -> Unit,
    onPeriodChanged: (UpdatesPeriodFilter) -> Unit,
    onTagToggled: (String) -> Unit,
    onQuickFilterSelected: (UpdatesQuickFilter) -> Unit,
    onResetFilters: () -> Unit,
) {
    val filterState = uiState.filterState
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filterState.query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = "Поиск по событиям") },
            placeholder = { Text(text = "Текст, ссылка или источник") },
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = false,
                    onClick = { onQuickFilterSelected(UpdatesQuickFilter.UNREAD) },
                    label = { Text("Quick: unread") },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { onQuickFilterSelected(UpdatesQuickFilter.TODAY) },
                    label = { Text("Quick: today") },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { onQuickFilterSelected(UpdatesQuickFilter.GITHUB_ONLY) },
                    label = { Text("Quick: github-only") },
                )
            }
            item {
                FilterChip(
                    selected = filterState.unreadOnly,
                    onClick = onUnreadOnlyToggled,
                    label = { Text("Непрочитанные") },
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filterState.source == null,
                    onClick = { onSourceChanged(null) },
                    label = { Text("Источник: все") },
                )
            }
            items(uiState.availableSources) { source ->
                FilterChip(
                    selected = filterState.source == source,
                    onClick = { onSourceChanged(source) },
                    label = { Text("Источник: $source") },
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filterState.period == UpdatesPeriodFilter.ALL,
                    onClick = { onPeriodChanged(UpdatesPeriodFilter.ALL) },
                    label = { Text("Период: все") },
                )
            }
            item {
                FilterChip(
                    selected = filterState.period == UpdatesPeriodFilter.TODAY,
                    onClick = { onPeriodChanged(UpdatesPeriodFilter.TODAY) },
                    label = { Text("Сегодня") },
                )
            }
            item {
                FilterChip(
                    selected = filterState.period == UpdatesPeriodFilter.LAST_7_DAYS,
                    onClick = { onPeriodChanged(UpdatesPeriodFilter.LAST_7_DAYS) },
                    label = { Text("7 дней") },
                )
            }
            item {
                FilterChip(
                    selected = filterState.period == UpdatesPeriodFilter.LAST_30_DAYS,
                    onClick = { onPeriodChanged(UpdatesPeriodFilter.LAST_30_DAYS) },
                    label = { Text("30 дней") },
                )
            }
        }

        if (uiState.availableTags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.availableTags) { tag ->
                    FilterChip(
                        selected = tag in filterState.selectedTags,
                        onClick = { onTagToggled(tag) },
                        label = { Text("#$tag") },
                    )
                }
            }
        }

        if (filterState.hasActiveFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Активные фильтры применены",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onResetFilters) {
                    Text("Сбросить")
                }
            }
        }
    }
}

@Composable
private fun UpdatesList(
    events: List<UpdateEvent>,
    markingIds: Set<Long>,
    onMarkAsRead: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (event.isRead) FontWeight.Normal else FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (event.isRead) "read" else "unread",
                        color =
                            if (event.isRead) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = event.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = event.linkUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "receivedAt=${event.receivedAtEpochMs}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!event.isRead) {
                    Button(
                        onClick = { onMarkAsRead(event.id) },
                        enabled = !markingIds.contains(event.id),
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .testTag(SmokeTestTags.updateMarkReadButton(event.id)),
                    ) {
                        Text(text = "Пометить как прочитанное")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(text = "Загружаю историю обновлений...", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun EmptyState(hasActiveFilters: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hasActiveFilters) {
            Text(text = "Ничего не найдено")
            Text(text = "Измените фильтры или сбросьте их")
        } else {
            Text(text = "Пока нет событий")
            Text(text = "Новые уведомления из Bot API появятся здесь")
        }
    }
}

@Composable
private fun TopActions(
    onGoToSubscriptions: () -> Unit,
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
            onClick = onGoToSubscriptions,
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(SmokeTestTags.UPDATES_OPEN_SUBSCRIPTIONS_BUTTON),
        ) { Text(text = "Открыть Subscriptions") }
        Button(onClick = onGoToSettings, modifier = Modifier.weight(1f)) { Text(text = "Открыть Settings") }
        Button(onClick = onLogout, modifier = Modifier.testTag(SmokeTestTags.UPDATES_LOGOUT_BUTTON)) {
            Text(text = "Выйти")
        }
    }
}
