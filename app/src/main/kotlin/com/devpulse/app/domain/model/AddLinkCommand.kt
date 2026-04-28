package com.devpulse.app.domain.model

data class AddLinkCommand(
    val link: String,
    val tags: List<String>,
    val filters: List<String>,
)
