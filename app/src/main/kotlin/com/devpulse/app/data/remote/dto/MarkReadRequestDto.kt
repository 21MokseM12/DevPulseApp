package com.devpulse.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkReadRequestDto(
    val notificationIds: List<Long>,
)
