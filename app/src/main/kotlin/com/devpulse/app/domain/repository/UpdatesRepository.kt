package com.devpulse.app.domain.repository

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.push.ParsedPushUpdate
import kotlinx.coroutines.flow.Flow

interface UpdatesRepository {
    fun observeUpdates(): Flow<List<UpdateEvent>>

    suspend fun saveIncomingUpdate(
        update: ParsedPushUpdate,
        receivedAtEpochMs: Long,
    ): Boolean

    suspend fun markAsRead(updateId: Long): Boolean
}
