package com.devpulse.app.data.repository

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
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotificationsRepositoryTest {
    @Test
    fun getNotifications_mapsDtosToDomain() =
        runTest {
            val repository =
                DefaultNotificationsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            notificationsResult =
                                RemoteCallResult.Success(
                                    data =
                                        NotificationListResponseDto(
                                            notifications =
                                                listOf(
                                                    NotificationDto(
                                                        id = 10L,
                                                        title = "Title",
                                                        description = "Body",
                                                        url = "https://example.org",
                                                        unread = false,
                                                        linkId = 777L,
                                                        receivedAt = "2026-05-13T20:00:00Z",
                                                        readAt = "2026-05-13T20:10:00Z",
                                                    ),
                                                ),
                                            limit = 20,
                                            offset = 0,
                                        ),
                                    statusCode = 200,
                                ),
                        ),
                )

            val result = repository.getNotifications(limit = 20, offset = 0, tags = listOf("news"))

            assertTrue(result is NotificationsResult.Success)
            val notification = (result as NotificationsResult.Success).notifications.first()
            assertEquals(10L, notification.id)
            assertEquals("777", notification.updateOwner)
        }

    @Test
    fun getUnreadCount_defaultsToZeroWhenFieldMissing() =
        runTest {
            val repository =
                DefaultNotificationsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            unreadCountResult =
                                RemoteCallResult.Success(
                                    data = UnreadCountResponseDto(unreadCount = null),
                                    statusCode = 200,
                                ),
                        ),
                )

            val result = repository.getUnreadCount()

            assertTrue(result is UnreadCountResult.Success)
            assertEquals(0, (result as UnreadCountResult.Success).unreadCount)
        }

    @Test
    fun markRead_returnsFailureForApiError() =
        runTest {
            val repository =
                DefaultNotificationsRepository(
                    remoteDataSource =
                        FakeRemoteDataSource(
                            markReadResult =
                                RemoteCallResult.ApiFailure(
                                    error =
                                        ApiError(
                                            kind = ApiErrorKind.BadRequest,
                                            userMessage = "Некорректный запрос",
                                        ),
                                    statusCode = 400,
                                ),
                        ),
                )

            val result = repository.markRead(notificationIds = listOf(1L, 2L))

            assertTrue(result is MarkReadResult.Failure)
            assertEquals("Некорректный запрос", (result as MarkReadResult.Failure).error.userMessage)
        }

    private class FakeRemoteDataSource(
        private val notificationsResult: RemoteCallResult<NotificationListResponseDto> =
            RemoteCallResult.Success(
                data =
                    NotificationListResponseDto(
                        notifications = emptyList(),
                        limit = 20,
                        offset = 0,
                    ),
                statusCode = 200,
            ),
        private val unreadCountResult: RemoteCallResult<UnreadCountResponseDto> =
            RemoteCallResult.Success(data = UnreadCountResponseDto(unreadCount = 0), statusCode = 200),
        private val markReadResult: RemoteCallResult<MarkReadResponseDto> =
            RemoteCallResult.Success(data = MarkReadResponseDto(updatedCount = 1), statusCode = 200),
    ) : DevPulseRemoteDataSource {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            throw UnsupportedOperationException()
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            throw UnsupportedOperationException()
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            throw UnsupportedOperationException()
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> = notificationsResult

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> = unreadCountResult

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> =
            markReadResult
    }
}
