package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.ClientCredentials
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClientCredentialsRequestDto(
    @field:Json(name = "login")
    val login: String,
)

fun ClientCredentialsRequestDto.toDomain(): ClientCredentials {
    return ClientCredentials(login = login)
}
