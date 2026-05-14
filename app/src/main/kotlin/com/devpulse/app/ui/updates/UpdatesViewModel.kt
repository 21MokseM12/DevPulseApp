package com.devpulse.app.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val unreadCount: Int = 0,
    val markingIds: Set<Long> = emptySet(),
    val actionErrorMessage: String? = null,
)

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        private val notificationsRepository: NotificationsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

        init {
            loadUpdates()
        }

        private fun loadUpdates() {
            viewModelScope.launch {
                val notificationsResult =
                    notificationsRepository.getNotifications(
                        limit = FEED_LIMIT,
                        offset = FEED_OFFSET,
                        tags = emptyList(),
                    )
                val unreadResult = notificationsRepository.getUnreadCount()

                _uiState.update { state ->
                    val events =
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
                        events = events,
                        unreadCount = unreadCount,
                        actionErrorMessage = resolveError(notificationsResult, unreadResult),
                    )
                }
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
                        unreadCount = if (marked) state.unreadCount else state.unreadCount + 1,
                        markingIds = state.markingIds - updateId,
                        actionErrorMessage = if (marked) null else "Не удалось отметить событие прочитанным.",
                    )
                }
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
        }
    }
