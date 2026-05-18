package com.devpulse.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationListResponseDto(
    @param:Json(name = "notifications")
    val notifications: List<NotificationDto>? = null,
    @param:Json(name = "items")
    val items: List<NotificationDto>? = null,
    val limit: Int? = null,
    val offset: Int? = null,
) {
    val resolvedNotifications: List<NotificationDto>
        get() = notifications ?: items.orEmpty()
}
