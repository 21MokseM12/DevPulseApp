package com.devpulse.app.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkReadRequestDto(
    val ids: List<Long>? = null,
    val markAll: Boolean? = null,
)
