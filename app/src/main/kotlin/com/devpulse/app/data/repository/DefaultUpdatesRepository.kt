package com.devpulse.app.data.repository

import com.devpulse.app.data.local.db.PushUpdateEntity
import com.devpulse.app.data.local.db.PushUpdatesDao
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.ParsedPushUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultUpdatesRepository
    @Inject
    constructor(
        private val pushUpdatesDao: PushUpdatesDao,
    ) : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> {
            return pushUpdatesDao.observeAll().map { entities ->
                entities.map { it.toDomain() }
            }
        }

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            val insertId =
                pushUpdatesDao.insert(
                    PushUpdateEntity(
                        remoteEventId = update.remoteEventId,
                        linkUrl = update.linkUrl,
                        title = update.title,
                        content = update.content,
                        receivedAtEpochMs = receivedAtEpochMs,
                        isRead = false,
                    ),
                )
            return insertId != -1L
        }

        override suspend fun markAsRead(updateId: Long): Boolean {
            return pushUpdatesDao.markAsRead(updateId) > 0
        }

        override suspend fun clearUpdates() {
            pushUpdatesDao.clearAll()
        }
    }

private fun PushUpdateEntity.toDomain(): UpdateEvent {
    return UpdateEvent(
        id = id,
        remoteEventId = remoteEventId,
        linkUrl = linkUrl,
        title = title,
        content = content,
        receivedAtEpochMs = receivedAtEpochMs,
        isRead = isRead,
    )
}
