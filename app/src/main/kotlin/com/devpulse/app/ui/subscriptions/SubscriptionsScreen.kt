package com.devpulse.app.ui.subscriptions

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.ui.testing.SmokeTestTags
import com.devpulse.app.ui.theme.Spacing
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
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onAddLinkInputChange = viewModel::onAddLinkInputChanged,
        onAddTagsInputChange = viewModel::onAddTagsInputChanged,
        onAddFiltersInputChange = viewModel::onAddFiltersInputChanged,
        onAddSubscription = viewModel::addSubscription,
        onPrepareFirstSubscriptionDraft = viewModel::prepareFirstSubscriptionDraft,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onTagFilterSelected = viewModel::onTagFilterSelected,
        onOnlyTaggedPresetToggled = viewModel::onOnlyTaggedPresetToggled,
        onWithFiltersPresetToggled = viewModel::onWithFiltersPresetToggled,
        onSortModeSelected = viewModel::onSortModeSelected,
        onClearSearch = viewModel::clearSearch,
        onRemoveRequested = viewModel::onRemoveRequested,
        onRemoveDismissed = viewModel::onRemoveDismissed,
        onRemoveConfirmed = viewModel::confirmRemove,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionsScreen(
    uiState: SubscriptionsUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onAddLinkInputChange: (String) -> Unit,
    onAddTagsInputChange: (String) -> Unit,
    onAddFiltersInputChange: (String) -> Unit,
    onAddSubscription: () -> Unit,
    onPrepareFirstSubscriptionDraft: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTagFilterSelected: (String?) -> Unit,
    onOnlyTaggedPresetToggled: () -> Unit,
    onWithFiltersPresetToggled: () -> Unit,
    onSortModeSelected: (SubscriptionsSortMode) -> Unit,
    onClearSearch: () -> Unit,
    onRemoveRequested: (TrackedLink) -> Unit,
    onRemoveDismissed: () -> Unit,
    onRemoveConfirmed: () -> Unit,
) {
    val addLinkFocusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AddSubscriptionForm(
            uiState = uiState,
            onAddLinkInputChange = onAddLinkInputChange,
            onAddTagsInputChange = onAddTagsInputChange,
            onAddFiltersInputChange = onAddFiltersInputChange,
            onAddSubscription = onAddSubscription,
            addLinkFocusRequester = addLinkFocusRequester,
        )
        SearchAndFiltersSection(
            uiState = uiState,
            onSearchQueryChanged = onSearchQueryChanged,
            onTagFilterSelected = onTagFilterSelected,
            onOnlyTaggedPresetToggled = onOnlyTaggedPresetToggled,
            onWithFiltersPresetToggled = onWithFiltersPresetToggled,
            onSortModeSelected = onSortModeSelected,
            onClearSearch = onClearSearch,
        )

        if (uiState.removeErrorMessage != null) {
            Text(
                text = uiState.removeErrorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        if (uiState.isStaleData) {
            Text(
                text = staleDataMessage(uiState.lastSyncAtEpochMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }

        if (uiState.isLoading) {
            LoadingState(message = "Загружаю подписки...")
            return
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (uiState.screenState) {
                SubscriptionsScreenState.Loading -> Unit
                SubscriptionsScreenState.Error -> {
                    ErrorState(
                        message = uiState.errorMessage ?: "Не удалось загрузить подписки.",
                        onRetry = onRetry,
                    )
                }

                SubscriptionsScreenState.Empty -> {
                    EmptyState(
                        onPrimaryAction = {
                            onPrepareFirstSubscriptionDraft()
                            addLinkFocusRequester.requestFocus()
                        },
                    )
                }

                SubscriptionsScreenState.Content -> {
                    if (uiState.links.isEmpty()) {
                        NoResultsState(onClearSearch = onClearSearch)
                    } else {
                        LinksContent(
                            links = uiState.links,
                            isRemoving = uiState.isRemoving,
                            onRemoveRequested = onRemoveRequested,
                        )
                    }
                }
            }
        }
    }

    if (uiState.pendingRemoval != null) {
        AlertDialog(
            onDismissRequest = onRemoveDismissed,
            title = { Text(text = "Удалить подписку?") },
            text = {
                Text(
                    text = uiState.pendingRemoval.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onRemoveConfirmed,
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_REMOVE_CONFIRM_BUTTON),
                ) {
                    Text(text = "Удалить", color = MaterialTheme.colorScheme.error)
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

@Composable
private fun SearchAndFiltersSection(
    uiState: SubscriptionsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onTagFilterSelected: (String?) -> Unit,
    onOnlyTaggedPresetToggled: () -> Unit,
    onWithFiltersPresetToggled: () -> Unit,
    onSortModeSelected: (SubscriptionsSortMode) -> Unit,
    onClearSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = uiState.searchState.query,
            onValueChange = onSearchQueryChanged,
            label = { Text(text = "Поиск по URL, tags, filters") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(SmokeTestTags.SUBSCRIPTIONS_SEARCH_INPUT),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            item {
                FilterChip(
                    selected = uiState.searchState.onlyTagged,
                    onClick = onOnlyTaggedPresetToggled,
                    label = { Text(text = "Only tagged") },
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_PRESET_ONLY_TAGGED),
                )
            }
            item {
                FilterChip(
                    selected = uiState.searchState.hasFiltersOnly,
                    onClick = onWithFiltersPresetToggled,
                    label = { Text(text = "With filters") },
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_PRESET_WITH_FILTERS),
                )
            }
            item {
                FilterChip(
                    selected = uiState.searchState.sortMode == SubscriptionsSortMode.RECENTLY_ADDED,
                    onClick = { onSortModeSelected(SubscriptionsSortMode.RECENTLY_ADDED) },
                    label = { Text(text = "Recently added") },
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_PRESET_RECENTLY_ADDED),
                )
            }
            item {
                FilterChip(
                    selected = uiState.searchState.sortMode == SubscriptionsSortMode.URL_ASCENDING,
                    onClick = { onSortModeSelected(SubscriptionsSortMode.URL_ASCENDING) },
                    label = { Text(text = "Sort by URL") },
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_SORT_BY_URL),
                )
            }
            items(uiState.availableTags, key = { tag -> tag }) { tag ->
                FilterChip(
                    selected = uiState.searchState.tagFilter.equals(tag, ignoreCase = true),
                    onClick = {
                        val next = if (uiState.searchState.tagFilter.equals(tag, ignoreCase = true)) null else tag
                        onTagFilterSelected(next)
                    },
                    label = { Text(text = "#$tag") },
                    modifier = Modifier.testTag(SmokeTestTags.subscriptionTagFilter(tag)),
                )
            }
        }
        if (hasActiveSearch(uiState)) {
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_CLEAR_SEARCH_BUTTON),
            ) {
                Text(text = "Сбросить поиск и фильтры")
            }
        }
    }
}

private fun hasActiveSearch(uiState: SubscriptionsUiState): Boolean {
    return uiState.searchState.hasActiveCriteria()
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
    addLinkFocusRequester: FocusRequester,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "Добавить подписку",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = uiState.addLinkInput,
                onValueChange = onAddLinkInputChange,
                label = { Text(text = "URL") },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(addLinkFocusRequester)
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
                    style = MaterialTheme.typography.bodySmall,
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
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(text = "Добавить ссылку")
                }
            }
        }
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
        contentPadding =
            PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.sm,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = links, key = { it.id }) { link ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SmokeTestTags.subscriptionRow(link.id)),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                            text = link.url,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onRemoveRequested(link) },
                            enabled = !isRemoving,
                            modifier = Modifier.testTag(SmokeTestTags.subscriptionRemoveButton(link.id)),
                        ) {
                            Text(
                                text = "Удалить",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (link.tags.isNotEmpty()) {
                        Text(
                            text = "Tags: ${link.tags.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (link.filters.isNotEmpty()) {
                        Text(
                            text = "Filters: ${link.filters.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(message: String) {
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
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(onPrimaryAction: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Список подписок пуст",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_EMPTY_TITLE),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Добавьте первую ссылку, чтобы начать отслеживание обновлений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_EMPTY_DESCRIPTION),
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_EMPTY_PRIMARY_BUTTON),
        ) {
            Text(text = "Добавить первую ссылку")
        }
    }
}

@Composable
private fun NoResultsState(onClearSearch: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Ничего не найдено",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "Измените критерии поиска или сбросьте фильтры",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = onClearSearch,
            modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_CLEAR_SEARCH_BUTTON),
        ) {
            Text(text = "Сбросить")
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Что-то пошло не так",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(onClick = onRetry) {
            Text(text = "Повторить")
        }
    }
}
