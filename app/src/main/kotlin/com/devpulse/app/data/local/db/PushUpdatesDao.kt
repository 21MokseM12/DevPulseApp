package com.devpulse.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PushUpdatesDao {
    @Query("SELECT * FROM updates_history ORDER BY receivedAtEpochMs DESC, id DESC")
    fun observeAll(): Flow<List<PushUpdateEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(update: PushUpdateEntity): Long
}
