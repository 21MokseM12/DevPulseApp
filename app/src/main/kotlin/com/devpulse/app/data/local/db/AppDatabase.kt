package com.devpulse.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PushUpdateEntity::class,
        CachedSubscriptionEntity::class,
        SubscriptionsSyncStateEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pushUpdatesDao(): PushUpdatesDao

    abstract fun subscriptionsCacheDao(): SubscriptionsCacheDao

    companion object {
        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS cached_session")
                }
            }
    }
}
