package com.devpulse.app.data.repository

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.toDomain
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultNotificationsRepository
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
    ) : NotificationsRepository {
        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): NotificationsResult {
            return when (
                val result =
                    remoteDataSource.getNotifications(
                        limit = limit,
                        offset = offset,
                        tags = tags,
                    )
            ) {
                is RemoteCallResult.Success -> {
                    NotificationsResult.Success(
                        notifications = result.data.notifications.map { it.toDomain() },
                    )
                }

                is RemoteCallResult.ApiFailure -> NotificationsResult.Failure(error = result.error)
                is RemoteCallResult.NetworkFailure -> NotificationsResult.Failure(error = result.error)
            }
        }

        override suspend fun getUnreadCount(): UnreadCountResult {
            return when (val result = remoteDataSource.getUnreadNotificationsCount()) {
                is RemoteCallResult.Success -> {
                    UnreadCountResult.Success(unreadCount = result.data.unreadCount ?: 0)
                }

                is RemoteCallResult.ApiFailure -> UnreadCountResult.Failure(error = result.error)
                is RemoteCallResult.NetworkFailure -> UnreadCountResult.Failure(error = result.error)
            }
        }

        override suspend fun markRead(notificationIds: List<Long>): MarkReadResult {
            return when (
                val result =
                    remoteDataSource.markNotificationsRead(
                        request = MarkReadRequestDto(ids = notificationIds),
                    )
            ) {
                is RemoteCallResult.Success -> MarkReadResult.Success(updatedCount = result.data.updatedCount)
                is RemoteCallResult.ApiFailure -> MarkReadResult.Failure(error = result.error)
                is RemoteCallResult.NetworkFailure -> MarkReadResult.Failure(error = result.error)
            }
        }
    }
