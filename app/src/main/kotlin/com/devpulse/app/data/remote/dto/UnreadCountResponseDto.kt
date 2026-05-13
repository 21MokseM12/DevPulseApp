package com.devpulse.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnreadCountResponseDto(
    @Json(name = "unreadCount")
    val unreadCount: Int?,
)
