package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.TrackedLink
import com.squareup.moshi.Json

data class LinkResponseDto(
    @field:Json(name = "id")
    val id: Long,
    @field:Json(name = "url")
    val url: String,
    @field:Json(name = "tags")
    val tags: List<String>? = null,
    @field:Json(name = "filters")
    val filters: List<String>? = null,
)

fun LinkResponseDto.toDomain(): TrackedLink {
    return TrackedLink(
        id = id,
        url = url,
        tags = tags.orEmpty(),
        filters = filters.orEmpty(),
    )
}
