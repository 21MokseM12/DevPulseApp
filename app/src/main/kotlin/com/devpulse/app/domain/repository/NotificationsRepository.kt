package com.devpulse.app.domain.repository

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.RemoteNotification

sealed interface NotificationsResult {
    data class Success(
        val notifications: List<RemoteNotification>,
    ) : NotificationsResult

    data class Failure(
        val error: ApiError,
    ) : NotificationsResult
}

sealed interface UnreadCountResult {
    data class Success(
        val unreadCount: Int,
    ) : UnreadCountResult

    data class Failure(
        val error: ApiError,
    ) : UnreadCountResult
}

sealed interface MarkReadResult {
    data class Success(
        val updatedCount: Int,
    ) : MarkReadResult

    data class Failure(
        val error: ApiError,
    ) : MarkReadResult
}

interface NotificationsRepository {
    suspend fun getNotifications(
        limit: Int,
        offset: Int,
        tags: List<String>,
    ): NotificationsResult

    suspend fun getUnreadCount(): UnreadCountResult

    suspend fun markRead(notificationIds: List<Long>): MarkReadResult
}
