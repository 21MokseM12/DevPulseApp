package com.devpulse.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_session")
data class CachedSessionEntity(
    @PrimaryKey val id: Int = 0,
    val clientLogin: String?,
    val updatedAtEpochMs: Long,
)
