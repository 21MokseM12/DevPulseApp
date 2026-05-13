package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.RemoteNotification
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: Long?,
    val title: String?,
    val content: String?,
    val link: String?,
    val tags: List<String>?,
    val isRead: Boolean?,
    val updateOwner: String?,
    val creationDate: String?,
)

fun NotificationDto.toDomain(): RemoteNotification {
    return RemoteNotification(
        id = id ?: 0L,
        title = title.orEmpty(),
        content = content.orEmpty(),
        link = link.orEmpty(),
        tags = tags.orEmpty(),
        isRead = isRead ?: false,
        updateOwner = updateOwner ?: "unknown",
        creationDate = creationDate.orEmpty(),
    )
}
