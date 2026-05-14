package com.devpulse.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationListResponseDto(
    val notifications: List<NotificationDto>,
    val limit: Int,
    val offset: Int,
)
