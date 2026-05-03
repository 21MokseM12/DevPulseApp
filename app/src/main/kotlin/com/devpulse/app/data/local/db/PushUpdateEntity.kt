package com.devpulse.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "updates_history",
    indices = [Index(value = ["remoteEventId"], unique = true)],
)
data class PushUpdateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val remoteEventId: String?,
    val linkUrl: String,
    val title: String,
    val content: String,
    val receivedAtEpochMs: Long,
    val isRead: Boolean,
)
