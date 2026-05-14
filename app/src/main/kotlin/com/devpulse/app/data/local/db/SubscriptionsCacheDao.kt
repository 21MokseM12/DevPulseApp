package com.devpulse.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SubscriptionsCacheDao {
    @Query("SELECT * FROM cached_subscriptions ORDER BY id DESC")
    suspend fun getAll(): List<CachedSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedSubscriptionEntity>)

    @Query("DELETE FROM cached_subscriptions")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entities: List<CachedSubscriptionEntity>) {
        clearAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }

    @Query("SELECT * FROM subscriptions_sync_state WHERE id = :id LIMIT 1")
    suspend fun getSyncState(id: Int = SubscriptionsSyncStateEntity.ID): SubscriptionsSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(entity: SubscriptionsSyncStateEntity)
}
