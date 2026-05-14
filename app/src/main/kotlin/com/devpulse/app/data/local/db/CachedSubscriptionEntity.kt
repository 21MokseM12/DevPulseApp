package com.devpulse.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_subscriptions")
data class CachedSubscriptionEntity(
    @PrimaryKey
    val id: Long,
    val url: String,
    val tagsSerialized: String,
    val filtersSerialized: String,
)
