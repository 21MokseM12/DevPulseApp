package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.ApiError
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiErrorResponseDto(
    @field:Json(name = "description")
    val description: String,
    @field:Json(name = "code")
    val code: String,
    @field:Json(name = "exceptionName")
    val exceptionName: String? = null,
    @field:Json(name = "exceptionMessage")
    val exceptionMessage: String? = null,
    @field:Json(name = "stacktrace")
    val stacktrace: List<String>? = null,
)

fun ApiErrorResponseDto.toDomain(): ApiError {
    return ApiError(
        description = description,
        code = code,
        exceptionName = exceptionName,
        exceptionMessage = exceptionMessage,
        stacktrace = stacktrace.orEmpty(),
    )
}
