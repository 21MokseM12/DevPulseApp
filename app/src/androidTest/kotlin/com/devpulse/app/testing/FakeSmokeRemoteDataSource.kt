package com.devpulse.app.testing

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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSmokeRemoteDataSource
    @Inject
    constructor() : DevPulseRemoteDataSource {
        private val lock = Any()
        private var links: MutableList<LinkResponseDto> = mutableListOf()
        private var notifications: MutableList<NotificationDto> = mutableListOf()
        private var nextLinkId = 1L

        init {
            reset()
        }

        fun reset() {
            synchronized(lock) {
                links =
                    mutableListOf(
                        LinkResponseDto(
                            id = 1L,
                            url = "https://devpulse.app/bootstrap",
                            tags = listOf("smoke"),
                            filters = listOf("default"),
                        ),
                    )
                notifications =
                    mutableListOf(
                        NotificationDto(
                            id = 1001L,
                            title = "Smoke update",
                            description = "Notification for smoke scenario",
                            url = "https://devpulse.app/bootstrap",
                            unread = true,
                            linkId = 1L,
                            receivedAt = Instant.now().toString(),
                            readAt = null,
                        ),
                    )
                nextLinkId = 2L
            }
        }

        fun setNotificationsForTesting(nextNotifications: List<NotificationDto>) {
            synchronized(lock) {
                notifications = nextNotifications.toMutableList()
            }
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return synchronized(lock) {
                RemoteCallResult.Success(
                    data = links.toList(),
                    statusCode = 200,
                )
            }
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            return synchronized(lock) {
                val existing = links.firstOrNull { it.url == request.link }
                if (existing != null) {
                    return@synchronized RemoteCallResult.Success(existing, 200)
                }

                val added =
                    LinkResponseDto(
                        id = nextLinkId++,
                        url = request.link,
                        tags = request.tags,
                        filters = request.filters,
                    )
                links.add(0, added)
                RemoteCallResult.Success(
                    data = added,
                    statusCode = 200,
                )
            }
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            return synchronized(lock) {
                links.removeAll { it.url == request.link }
                RemoteCallResult.Success(
                    data = BotApiMessageResponseDto(message = "Removed"),
                    statusCode = 200,
                )
            }
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            return synchronized(lock) {
                val page =
                    notifications
                        .drop(offset)
                        .take(limit)
                RemoteCallResult.Success(
                    data =
                        NotificationListResponseDto(
                            notifications = page,
                            limit = limit,
                            offset = offset,
                        ),
                    statusCode = 200,
                )
            }
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            return synchronized(lock) {
                val unreadCount = notifications.count { it.unread == true }
                RemoteCallResult.Success(
                    data = UnreadCountResponseDto(unreadCount = unreadCount),
                    statusCode = 200,
                )
            }
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            return synchronized(lock) {
                var updatedCount = 0
                notifications =
                    notifications
                        .map { notification ->
                            if (
                                notification.id != null &&
                                request.ids.orEmpty().contains(notification.id) &&
                                notification.unread == true
                            ) {
                                updatedCount += 1
                                notification.copy(unread = false, readAt = Instant.now().toString())
                            } else {
                                notification
                            }
                        }.toMutableList()

                RemoteCallResult.Success(
                    data = MarkReadResponseDto(updatedCount = updatedCount),
                    statusCode = 200,
                )
            }
        }
    }
