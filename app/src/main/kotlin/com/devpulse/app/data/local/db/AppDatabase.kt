package com.devpulse.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedSessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
