package com.devpulse.app.ui.updates

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.devpulse.app.data.repository.DefaultNotificationsRepository
import com.devpulse.app.domain.usecase.ApplyUpdatesFiltersUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesNotificationsFlowIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updatesViewModel_usesNotificationsFeedUnreadAndMarkRead() =
        runTest {
            val remote = RecordingRemoteDataSource()
            val repository = DefaultNotificationsRepository(remoteDataSource = remote)
            val viewModel =
                UpdatesViewModel(
                    notificationsRepository = repository,
                    applyUpdatesFiltersUseCase = ApplyUpdatesFiltersUseCase(),
                )
            advanceUntilIdle()

            val initialState = viewModel.uiState.value
            assertFalse(initialState.isLoading)
            assertEquals(2, initialState.events.size)
            assertEquals(2, initialState.unreadCount)
            assertTrue(initialState.events.any { it.id == 10L && !it.isRead })

            viewModel.markAsRead(10L)
            advanceUntilIdle()

            val afterMarkState = viewModel.uiState.value
            assertEquals(listOf(10L), remote.lastMarkReadIds)
            assertTrue(afterMarkState.events.any { it.id == 10L && it.isRead })
            assertEquals(1, afterMarkState.unreadCount)
            assertEquals(null, afterMarkState.actionErrorMessage)
        }

    @Test
    fun updatesViewModel_refreshRespectsActiveFilters() =
        runTest {
            val remote = RecordingRemoteDataSource()
            val repository = DefaultNotificationsRepository(remoteDataSource = remote)
            val viewModel =
                UpdatesViewModel(
                    notificationsRepository = repository,
                    applyUpdatesFiltersUseCase = ApplyUpdatesFiltersUseCase(),
                )
            advanceUntilIdle()

            viewModel.onQueryChanged("unread")
            advanceUntilIdle()
            viewModel.onUnreadOnlyToggled()
            advanceUntilIdle()
            assertEquals(listOf(10L, 11L), viewModel.uiState.value.events.map { it.id })

            remote.replaceNotifications(
                listOf(
                    NotificationDto(
                        id = 12L,
                        title = "Old read",
                        description = "Body C",
                        url = "https://example.com/c",
                        unread = false,
                        linkId = 103L,
                        receivedAt = "2026-05-10T08:00:00Z",
                        readAt = "2026-05-10T08:10:00Z",
                    ),
                    NotificationDto(
                        id = 13L,
                        title = "Fresh unread",
                        description = "Body D",
                        url = "https://example.com/d",
                        unread = true,
                        linkId = 104L,
                        receivedAt = "2026-05-14T13:00:00Z",
                        readAt = null,
                    ),
                ),
            )
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf(13L), viewModel.uiState.value.events.map { it.id })
            assertEquals(2, remote.requestHistory.size)
            assertEquals(100, remote.requestHistory[0].limit)
            assertEquals(0, remote.requestHistory[0].offset)
            assertEquals(emptyList<String>(), remote.requestHistory[0].tags)
            assertEquals(100, remote.requestHistory[1].limit)
            assertEquals(0, remote.requestHistory[1].offset)
            assertEquals(emptyList<String>(), remote.requestHistory[1].tags)
        }

    @Test
    fun updatesViewModel_refreshWithSelectedTag_forwardsTagsAndPreservesPagingRequest() =
        runTest {
            val remote = RecordingRemoteDataSource()
            val repository = DefaultNotificationsRepository(remoteDataSource = remote)
            val viewModel =
                UpdatesViewModel(
                    notificationsRepository = repository,
                    applyUpdatesFiltersUseCase = ApplyUpdatesFiltersUseCase(),
                )
            advanceUntilIdle()

            viewModel.onTagToggled("backend")
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()

            val lastRequest = remote.requestHistory.last()
            assertEquals(listOf("backend"), lastRequest.tags)
            assertEquals(100, lastRequest.limit)
            assertEquals(0, lastRequest.offset)
        }

    private class RecordingRemoteDataSource : DevPulseRemoteDataSource {
        private val notifications =
            mutableListOf(
                NotificationDto(
                    id = 10L,
                    title = "Unread",
                    description = "Body A",
                    url = "https://example.com/a",
                    unread = true,
                    linkId = 101L,
                    receivedAt = "2026-05-14T10:00:00Z",
                    readAt = null,
                ),
                NotificationDto(
                    id = 11L,
                    title = "Unread 2",
                    description = "Body B",
                    url = "https://example.com/b",
                    unread = true,
                    linkId = 102L,
                    receivedAt = "2026-05-14T11:00:00Z",
                    readAt = null,
                ),
            )
        var lastMarkReadIds: List<Long>? = null
            private set
        val requestHistory = mutableListOf<NotificationsRequest>()

        fun replaceNotifications(next: List<NotificationDto>) {
            notifications.clear()
            notifications.addAll(next)
        }

        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            error("Not used")
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            error("Not used")
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            error("Not used")
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            error("Not used")
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("Not used")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("Not used")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            requestHistory += NotificationsRequest(limit = limit, offset = offset, tags = tags)
            return RemoteCallResult.Success(
                data =
                    NotificationListResponseDto(
                        notifications = notifications.toList(),
                        limit = limit,
                        offset = offset,
                    ),
                statusCode = 200,
            )
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            val unread = notifications.count { it.unread == true }
            return RemoteCallResult.Success(
                data = UnreadCountResponseDto(unreadCount = unread),
                statusCode = 200,
            )
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            val ids = request.ids.orEmpty()
            lastMarkReadIds = ids
            val idsSet = ids.toSet()
            notifications.replaceAll { dto ->
                if (dto.id in idsSet) {
                    dto.copy(
                        unread = false,
                        readAt = "2026-05-14T12:00:00Z",
                    )
                } else {
                    dto
                }
            }
            return RemoteCallResult.Success(
                data = MarkReadResponseDto(updatedCount = ids.size),
                statusCode = 200,
            )
        }
    }

    private data class NotificationsRequest(
        val limit: Int,
        val offset: Int,
        val tags: List<String>,
    )
}
