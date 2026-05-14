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
            val viewModel = UpdatesViewModel(notificationsRepository = repository)
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
}
