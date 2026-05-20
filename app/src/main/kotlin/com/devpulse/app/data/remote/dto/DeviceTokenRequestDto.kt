package com.devpulse.app.data.remote.dto

import com.squareup.moshi.Json

data class DeviceTokenRequestDto(
    @param:Json(name = "token")
    val token: String,
    @param:Json(name = "platform")
    val platform: String = "android",
    @param:Json(name = "appVersion")
    val appVersion: String? = null,
    @param:Json(name = "deviceId")
    val deviceId: String? = null,
)
