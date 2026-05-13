package com.devpulse.app.domain.model

data class RemoteNotification(
    val id: Long,
    val title: String,
    val content: String,
    val link: String,
    val tags: List<String>,
    val isRead: Boolean,
    val updateOwner: String,
    val creationDate: String,
)
