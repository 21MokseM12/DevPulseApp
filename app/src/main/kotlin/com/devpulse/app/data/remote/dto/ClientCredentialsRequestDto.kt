package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.ClientCredentials
import com.squareup.moshi.Json

data class ClientCredentialsRequestDto(
    @field:Json(name = "login")
    val login: String,
    @field:Json(name = "password")
    val password: String,
)

fun ClientCredentialsRequestDto.toDomain(): ClientCredentials {
    return ClientCredentials(login = login, password = password)
}
