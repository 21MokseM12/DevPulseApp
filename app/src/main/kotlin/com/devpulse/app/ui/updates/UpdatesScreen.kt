package com.devpulse.app.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.OpenUpdatesDigestContextRequest
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.model.UpdatesQuickFilter
import com.devpulse.app.ui.testing.SmokeTestTags
import com.devpulse.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdatesRoute(
    onGoToSubscriptions: () -> Unit,
    onGoToSettings: () -> Unit,
    onLogout: () -> Unit,
    digestContextRequest: OpenUpdatesDigestContextRequest? = null,
    onDigestContextRequestHandled: () -> Unit = {},
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    LaunchedEffect(digestContextRequest) {
        val request = digestContextRequest ?: return@LaunchedEffect
        viewModel.applyDigestContext(
            unreadOnly = request.unreadOnly,
            periodStartEpochMs = request.periodStartEpochMs,
            periodEndEpochMs = request.periodEndEpochMs,
        )
        onDigestContextRequestHandled()
    }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    UpdatesScreen(
        uiState = uiState,
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
    onMarkAsRead: (Long) -> Unit,
    onQueryChanged: (String) -> Unit,
    onUnreadOnlyToggled: () -> Unit,
    onSourceChanged: (String?) -> Unit,
    onPeriodChanged: (UpdatesPeriodFilter) -> Unit,
    onTagToggled: (String) -> Unit,
    onQuickFilterSelected: (UpdatesQuickFilter) -> Unit,
    onResetFilters: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.unreadCount > 0) {
            Text(
                text = "Непрочитанных: ${uiState.unreadCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .testTag(SmokeTestTags.UPDATES_UNREAD_COUNT),
            )
        }

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
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }

        when {
            uiState.isLoading -> LoadingState()
            uiState.events.isEmpty() -> EmptyState(hasActiveFilters = uiState.filterState.hasActiveFilters)
            else ->
                UpdatesList(
                    events = uiState.events,
                    markingIds = uiState.markingIds,
                    onMarkAsRead = onMarkAsRead,
                )
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = filterState.query,
            onValueChange = onQueryChanged,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.UPDATES_SEARCH_INPUT),
            singleLine = true,
            label = { Text(text = "Поиск по событиям") },
            placeholder = { Text(text = "Текст, ссылка или источник") },
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onResetFilters,
                    modifier = Modifier.testTag(SmokeTestTags.UPDATES_RESET_FILTERS_BUTTON),
                ) {
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
        contentPadding =
            PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = events, key = { it.id }) { event ->
            UpdateEventCard(
                event = event,
                isMarking = event.id in markingIds,
                onMarkAsRead = onMarkAsRead,
            )
        }
    }
}

@Composable
private fun UpdateEventCard(
    event: UpdateEvent,
    isMarking: Boolean,
    onMarkAsRead: (Long) -> Unit,
) {
    val containerColor =
        if (!event.isRead) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surface
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (event.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!event.isRead) {
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }

            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = event.linkUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(event.receivedAtEpochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!event.isRead) {
                    OutlinedButton(
                        onClick = { onMarkAsRead(event.id) },
                        enabled = !isMarking,
                        modifier = Modifier.testTag(SmokeTestTags.updateMarkReadButton(event.id)),
                    ) {
                        if (isMarking) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                        } else {
                            Text(
                                text = "Прочитано",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    return DateFormat
        .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(epochMs))
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Загружаю историю обновлений...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(hasActiveFilters: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hasActiveFilters) {
            Text(
                text = "Ничего не найдено",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Измените фильтры или сбросьте их",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "Пока нет событий",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Новые уведомления из Bot API появятся здесь",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
