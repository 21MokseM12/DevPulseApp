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
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import kotlinx.coroutines.delay
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

        @Volatile
        private var loginDelayMs: Long = 0L

        @Volatile
        private var loginResult: RemoteCallResult<Unit> = RemoteCallResult.Success(Unit, 200)

        @Volatile
        private var registerDelayMs: Long = 0L

        @Volatile
        private var registerResult: RemoteCallResult<Unit> = RemoteCallResult.Success(Unit, 200)

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
                loginDelayMs = 0L
                loginResult = RemoteCallResult.Success(Unit, 200)
                registerDelayMs = 0L
                registerResult = RemoteCallResult.Success(Unit, 200)
            }
        }

        fun setLoginDelayForTesting(delayMs: Long) {
            loginDelayMs = delayMs.coerceAtLeast(0L)
        }

        fun setLoginFailureForTesting(message: String) {
            loginResult =
                RemoteCallResult.ApiFailure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = message,
                        ),
                    statusCode = 400,
                )
        }

        fun setLoginSuccessForTesting() {
            loginResult = RemoteCallResult.Success(Unit, 200)
        }

        fun setRegisterDelayForTesting(delayMs: Long) {
            registerDelayMs = delayMs.coerceAtLeast(0L)
        }

        fun setRegisterFailureForTesting(message: String) {
            registerResult =
                RemoteCallResult.ApiFailure(
                    error =
                        ApiError(
                            kind = ApiErrorKind.BadRequest,
                            userMessage = message,
                        ),
                    statusCode = 400,
                )
        }

        fun setRegisterSuccessForTesting() {
            registerResult = RemoteCallResult.Success(Unit, 200)
        }

        fun setNotificationsForTesting(nextNotifications: List<NotificationDto>) {
            synchronized(lock) {
                notifications = nextNotifications.toMutableList()
            }
        }

        fun setLinksForTesting(nextLinks: List<LinkResponseDto>) {
            synchronized(lock) {
                links = nextLinks.toMutableList()
                val maxKnownId = nextLinks.maxOfOrNull { it.id } ?: 0L
                nextLinkId = maxKnownId + 1L
            }
        }

        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            val delayMs = loginDelayMs
            if (delayMs > 0L) {
                delay(delayMs)
            }
            return loginResult
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            val delayMs = registerDelayMs
            if (delayMs > 0L) {
                delay(delayMs)
            }
            return registerResult
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
