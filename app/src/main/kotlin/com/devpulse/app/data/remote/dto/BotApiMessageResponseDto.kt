package com.devpulse.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BotApiMessageResponseDto(
    val message: String,
)
