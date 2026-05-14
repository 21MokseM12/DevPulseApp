package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.RemoteNotification
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: Long?,
    val title: String?,
    val description: String?,
    val url: String?,
    val unread: Boolean?,
    val linkId: Long?,
    val receivedAt: String?,
    val readAt: String?,
)

fun NotificationDto.toDomain(): RemoteNotification {
    return RemoteNotification(
        id = id ?: 0L,
        title = title.orEmpty(),
        content = description.orEmpty(),
        link = url.orEmpty(),
        tags = emptyList(),
        isRead = unread?.not() ?: (readAt != null),
        updateOwner = linkId?.toString() ?: "unknown",
        creationDate = receivedAt.orEmpty(),
    )
}
