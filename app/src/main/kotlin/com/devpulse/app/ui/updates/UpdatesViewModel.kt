package com.devpulse.app.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesFilterState
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.model.UpdatesQuickFilter
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
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
    val events: List<UpdateEvent> = emptyList(),
    val allEvents: List<UpdateEvent> = emptyList(),
    val unreadCount: Int = 0,
    val filterState: UpdatesFilterState = UpdatesFilterState(),
    val availableSources: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val markingIds: Set<Long> = emptySet(),
    val actionErrorMessage: String? = null,
)

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        private val notificationsRepository: NotificationsRepository,
        private val applyUpdatesFiltersUseCase: ApplyUpdatesFiltersUseCase,
    ) : ViewModel() {
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
                state.copy(
                    filterState =
                        state.filterState.copy(
                            period = period,
                            periodStartEpochMs = null,
                            periodEndEpochMs = null,
                        ),
                )
            }
            applyFilters()
        }

        fun onTagToggled(tag: String) {
            _uiState.update { state ->
                val nextTags =
                    if (tag in state.filterState.selectedTags) {
                        state.filterState.selectedTags - tag
                    } else {
                        state.filterState.selectedTags + tag
                    }
                state.copy(filterState = state.filterState.copy(selectedTags = nextTags))
            }
            applyFilters()
        }

        fun applyQuickFilter(filter: UpdatesQuickFilter) {
            _uiState.update { state ->
                val nextState =
                    when (filter) {
                        UpdatesQuickFilter.UNREAD ->
                            state.filterState.copy(
                                unreadOnly = true,
                                periodStartEpochMs = null,
                                periodEndEpochMs = null,
                            )
                        UpdatesQuickFilter.TODAY ->
                            state.filterState.copy(
                                period = UpdatesPeriodFilter.TODAY,
                                periodStartEpochMs = null,
                                periodEndEpochMs = null,
                            )
                        UpdatesQuickFilter.GITHUB_ONLY -> state.filterState.copy(source = SOURCE_GITHUB)
                    }
                state.copy(filterState = nextState)
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
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val requestTags = _uiState.value.filterState.selectedTags.toList().sorted()
                val notificationsResult =
                    notificationsRepository.getNotifications(
                        limit = FEED_LIMIT,
                        offset = FEED_OFFSET,
                        tags = requestTags,
                    )
                val unreadResult = notificationsRepository.getUnreadCount()

                _uiState.update { state ->
                    val allEvents =
                        when (notificationsResult) {
                            is NotificationsResult.Success ->
                                notificationsResult.notifications.map { it.toUpdateEvent() }
                            is NotificationsResult.Failure -> emptyList()
                        }
                    val unreadCount =
                        when (unreadResult) {
                            is UnreadCountResult.Success -> unreadResult.unreadCount
                            is UnreadCountResult.Failure -> state.unreadCount
                        }
                    state.copy(
                        isLoading = false,
                        allEvents = allEvents,
                        unreadCount = unreadCount,
                        availableSources = allEvents.map { it.source }.filter { it.isNotBlank() }.distinct().sorted(),
                        availableTags = allEvents.flatMap { it.tags }.distinct().sorted(),
                        actionErrorMessage = resolveError(notificationsResult, unreadResult),
                    )
                }
                applyFilters()
            }
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

        private fun RemoteNotification.toUpdateEvent(): UpdateEvent {
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
            const val SEARCH_DEBOUNCE_MS = 300L
            const val SOURCE_GITHUB = "github"
        }
    }
