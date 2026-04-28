package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.AddLinkCommand
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AddLinkRequestDto(
    @field:Json(name = "link")
    val link: String,
    @field:Json(name = "tags")
    val tags: List<String> = emptyList(),
    @field:Json(name = "filters")
    val filters: List<String> = emptyList(),
)

fun AddLinkRequestDto.toDomain(): AddLinkCommand {
    return AddLinkCommand(
        link = link,
        tags = tags,
        filters = filters,
    )
}
