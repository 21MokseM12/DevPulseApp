package com.devpulse.app.domain.model

data class TrackedLink(
    val id: Long,
    val url: String,
    val tags: List<String>,
    val filters: List<String>,
)
