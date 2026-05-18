package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.RemoteNotification
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: Long? = null,
    val title: String? = null,
    @param:Json(name = "description")
    val description: String? = null,
    @param:Json(name = "content")
    val content: String? = null,
    @param:Json(name = "url")
    val url: String? = null,
    @param:Json(name = "link")
    val link: String? = null,
    @param:Json(name = "unread")
    val unread: Boolean? = null,
    @param:Json(name = "isRead")
    val isRead: Boolean? = null,
    @param:Json(name = "is_read")
    val isReadSnakeCase: Boolean? = null,
    @param:Json(name = "linkId")
    val linkId: Long? = null,
    @param:Json(name = "link_id")
    val linkIdSnakeCase: Long? = null,
    @param:Json(name = "receivedAt")
    val receivedAt: String? = null,
    @param:Json(name = "received_at")
    val receivedAtSnakeCase: String? = null,
    @param:Json(name = "createdAt")
    val createdAt: String? = null,
    @param:Json(name = "created_at")
    val createdAtSnakeCase: String? = null,
    @param:Json(name = "readAt")
    val readAt: String? = null,
    @param:Json(name = "read_at")
    val readAtSnakeCase: String? = null,
    @param:Json(name = "tags")
    val tags: List<String>? = null,
    @param:Json(name = "updateOwner")
    val updateOwner: String? = null,
    @param:Json(name = "update_owner")
    val updateOwnerSnakeCase: String? = null,
    @param:Json(name = "source")
    val source: String? = null,
)

fun NotificationDto.toDomain(): RemoteNotification {
    val resolvedReadAt = readAt ?: readAtSnakeCase
    val resolvedIsRead =
        isRead ?: isReadSnakeCase ?: unread?.not() ?: (resolvedReadAt != null)
    val resolvedCreationDate = receivedAt ?: receivedAtSnakeCase ?: createdAt ?: createdAtSnakeCase
    val resolvedOwner =
        updateOwner ?: updateOwnerSnakeCase ?: source ?: linkId ?: linkIdSnakeCase
    return RemoteNotification(
        id = id ?: 0L,
        title = title.orEmpty(),
        content = description ?: content.orEmpty(),
        link = url ?: link.orEmpty(),
        tags = tags.orEmpty(),
        isRead = resolvedIsRead,
        updateOwner = resolvedOwner?.toString() ?: "unknown",
        creationDate = resolvedCreationDate.orEmpty(),
    )
}
