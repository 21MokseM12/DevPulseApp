package com.devpulse.app.domain.model

data class UpdateEvent(
    val id: Long,
    val remoteEventId: String?,
    val linkUrl: String,
    val title: String,
    val content: String,
    val receivedAtEpochMs: Long,
    val isRead: Boolean,
)
