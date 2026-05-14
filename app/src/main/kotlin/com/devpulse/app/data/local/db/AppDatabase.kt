package com.devpulse.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PushUpdateEntity::class,
        CachedSubscriptionEntity::class,
        SubscriptionsSyncStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pushUpdatesDao(): PushUpdatesDao

    abstract fun subscriptionsCacheDao(): SubscriptionsCacheDao
}
