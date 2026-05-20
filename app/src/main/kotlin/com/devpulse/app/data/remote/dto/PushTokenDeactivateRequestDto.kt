package com.devpulse.app.data.remote.dto

import com.squareup.moshi.Json

data class PushTokenDeactivateRequestDto(
    @param:Json(name = "token")
    val token: String,
    @param:Json(name = "platform")
    val platform: String = "android",
)
