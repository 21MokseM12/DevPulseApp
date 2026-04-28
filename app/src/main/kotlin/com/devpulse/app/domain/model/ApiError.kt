package com.devpulse.app.domain.model

data class ApiError(
    val description: String,
    val code: String,
    val exceptionName: String?,
    val exceptionMessage: String?,
    val stacktrace: List<String>,
)
