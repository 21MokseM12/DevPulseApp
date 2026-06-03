package com.devpulse.app.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesFilterState
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import com.devpulse.app.domain.usecase.ApplyUpdatesFiltersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlin.math.max

data class UpdatesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val events: List<UpdateEvent> = emptyList(),
    val allEvents: List<UpdateEvent> = emptyList(),
    val unreadCount: Int = 0,
    val filterState: UpdatesFilterState = UpdatesFilterState(),
    val availableSources: List<String> = emptyList(),
    val availableLinkFilters: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val markingIds: Set<Long> = emptySet(),
    val actionErrorMessage: String? = null,
)

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        private val notificationsRepository: NotificationsRepository,
        private val subscriptionsRepository: SubscriptionsRepository,
        private val applyUpdatesFiltersUseCase: ApplyUpdatesFiltersUseCase,
    ) : ViewModel() {
        constructor(
            notificationsRepository: NotificationsRepository,
            applyUpdatesFiltersUseCase: ApplyUpdatesFiltersUseCase,
        ) : this(
            notificationsRepository = notificationsRepository,
            subscriptionsRepository = NoOpSubscriptionsRepository,
            applyUpdatesFiltersUseCase = applyUpdatesFiltersUseCase,
        )

        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()
        private var searchDebounceJob: Job? = null

        init {
            loadUpdates()
        }

        fun refresh() {
            loadUpdates()
        }

        fun onQueryChanged(query: String) {
            _uiState.update { state ->
                state.copy(filterState = state.filterState.copy(query = query))
            }
            searchDebounceJob?.cancel()
            searchDebounceJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    applyFilters()
                }
        }

        fun onUnreadOnlyToggled() {
            _uiState.update { state ->
                state.copy(
                    filterState =
                        state.filterState.copy(
                            unreadOnly = !state.filterState.unreadOnly,
                        ),
                )
            }
            applyFilters()
        }

        fun onSourceChanged(source: String?) {
            _uiState.update { state ->
                state.copy(filterState = state.filterState.copy(source = source))
            }
            applyFilters()
        }

        fun onPeriodChanged(period: UpdatesPeriodFilter) {
            _uiState.update { state ->
                val shouldDropLegacyFilters = period == UpdatesPeriodFilter.ALL
                state.copy(
                    filterState =
                        state.filterState.copy(
                            source = if (shouldDropLegacyFilters) null else state.filterState.source,
                            period = period,
                            periodStartEpochMs = null,
                            periodEndEpochMs = null,
                            selectedLinkFilters =
                                if (shouldDropLegacyFilters) {
                                    emptySet()
                                } else {
                                    state.filterState.selectedLinkFilters
                                },
                        ),
                )
            }
            applyFilters()
        }

        fun onTagToggled(tag: String) {
            val normalizedTag = normalizeTag(tag) ?: return
            _uiState.update { state ->
                val nextTags =
                    if (normalizedTag in state.filterState.selectedTags) {
                        state.filterState.selectedTags - normalizedTag
                    } else {
                        state.filterState.selectedTags + normalizedTag
                    }
                state.copy(filterState = state.filterState.copy(selectedTags = nextTags))
            }
            applyFilters()
        }

        fun onLinkFilterToggled(filter: String) {
            val normalizedFilter = normalizeLinkFilter(filter) ?: return
            _uiState.update { state ->
                val nextFilters =
                    if (normalizedFilter in state.filterState.selectedLinkFilters) {
                        state.filterState.selectedLinkFilters - normalizedFilter
                    } else {
                        state.filterState.selectedLinkFilters + normalizedFilter
                    }
                state.copy(filterState = state.filterState.copy(selectedLinkFilters = nextFilters))
            }
            applyFilters()
        }

        fun resetFilters() {
            searchDebounceJob?.cancel()
            _uiState.update { state ->
                state.copy(filterState = UpdatesFilterState())
            }
            applyFilters()
        }

        fun applyDigestContext(
            unreadOnly: Boolean,
            periodStartEpochMs: Long?,
            periodEndEpochMs: Long?,
        ) {
            _uiState.update { state ->
                state.copy(
                    filterState =
                        state.filterState.copy(
                            unreadOnly = unreadOnly,
                            periodStartEpochMs = periodStartEpochMs,
                            periodEndEpochMs = periodEndEpochMs,
                        ),
                )
            }
            applyFilters()
        }

        private fun loadUpdates() {
            val currentState = _uiState.value
            if (currentState.isRefreshing || (currentState.isLoading && currentState.allEvents.isNotEmpty())) {
                return
            }
            val hasExistingContent = currentState.allEvents.isNotEmpty()
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoading = !hasExistingContent,
                        isRefreshing = hasExistingContent,
                    )
                }
                val requestTags =
                    _uiState.value.filterState.selectedTags
                        .mapNotNull(::normalizeTag)
                        .toSet()
                        .toList()
                        .sorted()
                val notificationsResult = loadAllNotifications(requestTags = requestTags)
                val unreadResult = notificationsRepository.getUnreadCount()
                val subscriptionsResult = subscriptionsRepository.getSubscriptions(forceRefresh = false)

                _uiState.update { state ->
                    val links =
                        when (subscriptionsResult) {
                            is SubscriptionsResult.Success -> subscriptionsResult.links
                            is SubscriptionsResult.Failure -> emptyList()
                        }
                    val linkFiltersByNormalizedUrl =
                        links
                            .mapNotNull { link ->
                                val normalizedUrl = normalizeUrl(link.url) ?: return@mapNotNull null
                                normalizedUrl to collectDistinctLinkFilters(link.filters)
                            }.filter { it.second.isNotEmpty() }
                            .sortedByDescending { it.first.length }
                    val allEvents =
                        when (notificationsResult) {
                            is NotificationsResult.Success ->
                                notificationsResult.notifications.map { notification ->
                                    notification.toUpdateEvent(
                                        linkFilters = resolveLinkFilters(notification.link, linkFiltersByNormalizedUrl),
                                    )
                                }
                            is NotificationsResult.Failure -> emptyList()
                        }
                    val unreadCount =
                        when (unreadResult) {
                            is UnreadCountResult.Success -> unreadResult.unreadCount
                            is UnreadCountResult.Failure -> state.unreadCount
                        }
                    val availableTags = collectAvailableTags(allEvents)
                    val availableTagKeys = availableTags.mapNotNull(::normalizeTag).toSet()
                    val selectedTags =
                        state.filterState.selectedTags
                            .mapNotNull(::normalizeTag)
                            .filter { it in availableTagKeys }
                            .toSet()
                    val availableLinkFilters = collectAvailableLinkFilters(links)
                    val selectedLinkFilters =
                        state.filterState.selectedLinkFilters
                            .mapNotNull(::normalizeLinkFilter)
                            .filter { it in availableLinkFilters.mapNotNull(::normalizeLinkFilter).toSet() }
                            .toSet()
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allEvents = allEvents,
                        unreadCount = unreadCount,
                        availableSources = allEvents.map { it.source }.filter { it.isNotBlank() }.distinct().sorted(),
                        availableLinkFilters = availableLinkFilters,
                        availableTags = availableTags,
                        filterState =
                            state.filterState.copy(
                                selectedTags = selectedTags,
                                selectedLinkFilters = selectedLinkFilters,
                            ),
                        actionErrorMessage = resolveError(notificationsResult, unreadResult),
                    )
                }
                applyFilters()
            }
        }

        private suspend fun loadAllNotifications(requestTags: List<String>): NotificationsResult {
            val collected = mutableListOf<RemoteNotification>()
            var offset = FEED_OFFSET
            var page = 0
            while (page < MAX_FEED_PAGES) {
                when (
                    val pageResult =
                        notificationsRepository.getNotifications(
                            limit = FEED_LIMIT,
                            offset = offset,
                            tags = requestTags,
                        )
                ) {
                    is NotificationsResult.Failure -> {
                        return if (collected.isEmpty()) {
                            pageResult
                        } else {
                            NotificationsResult.Success(
                                notifications = collected.toList(),
                            )
                        }
                    }

                    is NotificationsResult.Success -> {
                        val pageNotifications = pageResult.notifications
                        if (pageNotifications.isEmpty()) break
                        collected += pageNotifications
                        if (pageNotifications.size < FEED_LIMIT) break
                        offset += pageNotifications.size
                        page += 1
                    }
                }
            }
            return NotificationsResult.Success(notifications = collected.toList())
        }

        fun markAsRead(updateId: Long) {
            val current = _uiState.value
            val target = current.events.firstOrNull { it.id == updateId } ?: return
            if (target.isRead || current.markingIds.contains(updateId)) return

            _uiState.update { state ->
                state.copy(
                    events =
                        state.events.map { event ->
                            if (event.id == updateId) event.copy(isRead = true) else event
                        },
                    unreadCount = max(0, state.unreadCount - 1),
                    markingIds = state.markingIds + updateId,
                    actionErrorMessage = null,
                )
            }

            viewModelScope.launch {
                val marked =
                    notificationsRepository.markRead(notificationIds = listOf(updateId)) is MarkReadResult.Success
                _uiState.update { state ->
                    val rollbackEvents =
                        if (marked) {
                            state.events
                        } else {
                            state.events.map { event ->
                                if (event.id == updateId) event.copy(isRead = false) else event
                            }
                        }
                    state.copy(
                        events = rollbackEvents,
                        allEvents =
                            if (marked) {
                                state.allEvents.map { event ->
                                    if (event.id == updateId) event.copy(isRead = true) else event
                                }
                            } else {
                                state.allEvents
                            },
                        unreadCount = if (marked) state.unreadCount else state.unreadCount + 1,
                        markingIds = state.markingIds - updateId,
                        actionErrorMessage = if (marked) null else "Не удалось отметить событие прочитанным.",
                    )
                }
                applyFilters()
            }
        }

        private fun applyFilters() {
            _uiState.update { state ->
                state.copy(
                    events = applyUpdatesFiltersUseCase(state.allEvents, state.filterState),
                )
            }
        }

        private fun resolveError(
            notificationsResult: NotificationsResult,
            unreadResult: UnreadCountResult,
        ): String? {
            return when {
                notificationsResult is NotificationsResult.Failure -> notificationsResult.error.userMessage
                unreadResult is UnreadCountResult.Failure -> unreadResult.error.userMessage
                else -> null
            }
        }

        private fun RemoteNotification.toUpdateEvent(linkFilters: List<String>): UpdateEvent {
            return UpdateEvent(
                id = id,
                remoteEventId = id.toString(),
                linkUrl = link,
                title = title,
                content = content,
                receivedAtEpochMs = creationDate.toEpochMsOrZero(),
                isRead = isRead,
                source = updateOwner,
                tags = tags,
                linkFilters = linkFilters,
            )
        }

        private fun String.toEpochMsOrZero(): Long {
            return try {
                Instant.parse(this).toEpochMilli()
            } catch (_: DateTimeParseException) {
                0L
            }
        }

        private companion object {
            const val FEED_LIMIT = 100
            const val FEED_OFFSET = 0
            const val MAX_FEED_PAGES = 10
            const val SEARCH_DEBOUNCE_MS = 300L
        }

        private object NoOpSubscriptionsRepository : SubscriptionsRepository {
            override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult {
                return SubscriptionsResult.Success(emptyList())
            }

            override suspend fun addSubscription(
                link: String,
                tags: List<String>,
                filters: List<String>,
            ): SubscriptionsResult {
                return SubscriptionsResult.Success(emptyList())
            }

            override suspend fun removeSubscription(link: String): SubscriptionsResult {
                return SubscriptionsResult.Success(emptyList())
            }
        }

        private fun collectAvailableTags(events: List<UpdateEvent>): List<String> {
            val tagsByNormalized = linkedMapOf<String, String>()
            events.forEach { event ->
                event.tags.forEach { rawTag ->
                    val normalizedTag = normalizeTag(rawTag) ?: return@forEach
                    tagsByNormalized.putIfAbsent(normalizedTag, rawTag.trim())
                }
            }
            return tagsByNormalized.entries.sortedBy { it.key }.map { it.value }
        }

        private fun normalizeTag(rawTag: String): String? = rawTag.trim().lowercase().takeIf { it.isNotEmpty() }

        private fun collectAvailableLinkFilters(links: List<TrackedLink>): List<String> {
            val filtersByNormalized = linkedMapOf<String, String>()
            links.forEach { link ->
                link.filters.forEach { rawFilter ->
                    val normalizedFilter = normalizeLinkFilter(rawFilter) ?: return@forEach
                    filtersByNormalized.putIfAbsent(normalizedFilter, rawFilter.trim())
                }
            }
            return filtersByNormalized.entries.sortedBy { it.key }.map { it.value }
        }

        private fun collectDistinctLinkFilters(filters: List<String>): List<String> {
            return filters
                .mapNotNull(::normalizeLinkFilter)
                .distinct()
        }

        private fun resolveLinkFilters(
            eventUrl: String,
            linkFiltersByNormalizedUrl: List<Pair<String, List<String>>>,
        ): List<String> {
            val normalizedEventUrl = normalizeUrl(eventUrl) ?: return emptyList()
            return linkFiltersByNormalizedUrl
                .firstOrNull { (normalizedUrl, _) -> normalizedEventUrl.startsWith(normalizedUrl) }
                ?.second
                .orEmpty()
        }

        private fun normalizeUrl(value: String): String? {
            val normalized = value.trim().lowercase().trimEnd('/')
            return normalized.takeIf { it.isNotEmpty() }
        }

        private fun normalizeLinkFilter(rawFilter: String): String? {
            return rawFilter.trim().lowercase().takeIf { it.isNotEmpty() }
        }
    }
