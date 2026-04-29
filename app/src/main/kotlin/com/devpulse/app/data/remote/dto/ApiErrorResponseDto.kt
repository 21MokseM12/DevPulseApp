package com.devpulse.app.data.remote.dto

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.squareup.moshi.Json

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
        kind = ApiErrorKind.Unknown,
        userMessage = description,
        statusCode = null,
        code = code,
        technicalDescription = exceptionMessage ?: exceptionName,
    )
}
