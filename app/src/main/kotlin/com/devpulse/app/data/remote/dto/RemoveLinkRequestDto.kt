package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.RemoveLinkCommand
import com.squareup.moshi.Json

data class RemoveLinkRequestDto(
    @field:Json(name = "link")
    val link: String,
)

fun RemoveLinkRequestDto.toDomain(): RemoveLinkCommand {
    return RemoveLinkCommand(link = link)
}
