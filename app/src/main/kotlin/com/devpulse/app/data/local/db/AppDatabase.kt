package com.devpulse.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedSessionEntity::class,
        PushUpdateEntity::class,
        CachedSubscriptionEntity::class,
        SubscriptionsSyncStateEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun pushUpdatesDao(): PushUpdatesDao

    abstract fun subscriptionsCacheDao(): SubscriptionsCacheDao
}
