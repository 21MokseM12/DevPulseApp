package com.devpulse.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions_sync_state")
data class SubscriptionsSyncStateEntity(
    @PrimaryKey
    val id: Int = ID,
    val lastSyncAtEpochMs: Long?,
    val isStale: Boolean,
) {
    companion object {
        const val ID: Int = 0
    }
}
