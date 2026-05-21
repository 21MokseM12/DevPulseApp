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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devpulse.app.R
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
        onGroupByTagsPresetToggled = viewModel::onGroupByTagsPresetToggled,
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
    onGroupByTagsPresetToggled: () -> Unit,
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
            onGroupByTagsPresetToggled = onGroupByTagsPresetToggled,
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
                        if (uiState.searchState.groupByTags) {
                            LinksContentGrouped(
                                groups = uiState.groupedLinks,
                                isRemoving = uiState.isRemoving,
                                onRemoveRequested = onRemoveRequested,
                            )
                        } else {
                            LinksContentFlat(
                                links = uiState.links,
                                isRemoving = uiState.isRemoving,
                                onRemoveRequested = onRemoveRequested,
                            )
                        }
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
    onGroupByTagsPresetToggled: () -> Unit,
    onClearSearch: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = uiState.searchState.query,
            onValueChange = onSearchQueryChanged,
            label = { Text(text = "Поиск по URL, тегам и фильтрам") },
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
                    selected = uiState.searchState.groupByTags,
                    onClick = onGroupByTagsPresetToggled,
                    label = { Text(text = "По тегам") },
                    modifier = Modifier.testTag(SmokeTestTags.SUBSCRIPTIONS_PRESET_GROUP_BY_TAGS),
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
                label = { Text(text = "Filters type:value (через запятую)") },
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
private fun LinksContentGrouped(
    groups: List<SubscriptionTagGroup>,
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
        groups.forEach { group ->
            val groupKey = group.tag?.trim().orEmpty().ifBlank { "untagged" }.lowercase()
            item(key = "header_$groupKey") {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs, bottom = Spacing.xs)
                            .testTag(SmokeTestTags.subscriptionTagGroupHeader(group.tag)),
                )
            }
            items(
                items = group.items,
                key = { link -> "${groupKey}_${link.id}" },
            ) { link ->
                SubscriptionLinkCard(
                    link = link,
                    isRemoving = isRemoving,
                    onRemoveRequested = onRemoveRequested,
                )
            }
        }
    }
}

@Composable
private fun LinksContentFlat(
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
            SubscriptionLinkCard(
                link = link,
                isRemoving = isRemoving,
                onRemoveRequested = onRemoveRequested,
            )
        }
    }
}

@Composable
private fun SubscriptionLinkCard(
    link: TrackedLink,
    isRemoving: Boolean,
    onRemoveRequested: (TrackedLink) -> Unit,
) {
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinkPlatformIcon(url = link.url)
                    Text(
                        text = formatLinkDisplayName(link.url),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
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
                    text = "Теги: ${link.tags.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (link.filters.isNotEmpty()) {
                Text(
                    text = "Фильтры: ${link.filters.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class LinkPlatform { GITHUB, STACKOVERFLOW, UNKNOWN }

private fun detectPlatform(url: String): LinkPlatform {
    val lower = url.lowercase()
    return when {
        lower.contains("github.com") -> LinkPlatform.GITHUB
        lower.contains("stackoverflow.com") -> LinkPlatform.STACKOVERFLOW
        else -> LinkPlatform.UNKNOWN
    }
}

@Composable
private fun LinkPlatformIcon(url: String) {
    val platform = detectPlatform(url)
    when (platform) {
        LinkPlatform.GITHUB ->
            Icon(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = "GitHub",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        LinkPlatform.STACKOVERFLOW ->
            Icon(
                painter = painterResource(R.drawable.ic_stackoverflow),
                contentDescription = "Stack Overflow",
                tint = Color(0xFFF48024),
                modifier = Modifier.size(16.dp),
            )
        LinkPlatform.UNKNOWN -> return
    }
    Spacer(modifier = Modifier.width(Spacing.sm))
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
