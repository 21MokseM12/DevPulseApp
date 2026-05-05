package com.devpulse.app.domain.model

enum class ApiErrorKind {
    BadRequest,
    NotFound,
    Configuration,
    NetworkTimeout,
    Network,
    Unknown,
}

data class ApiError(
    val kind: ApiErrorKind,
    val userMessage: String,
    val statusCode: Int? = null,
    val code: String? = null,
    val technicalDescription: String? = null,
)
