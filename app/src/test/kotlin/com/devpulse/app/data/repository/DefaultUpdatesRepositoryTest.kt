package com.devpulse.app.data.repository

import com.devpulse.app.data.local.db.PushUpdateEntity
import com.devpulse.app.data.local.db.PushUpdatesDao
import com.devpulse.app.push.ParsedPushUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUpdatesRepositoryTest {
    @Test
    fun saveIncomingUpdate_whenInsertSuccess_returnsTrue() {
        runTest {
            val dao = FakePushUpdatesDao(insertResult = 10L)
            val repository = DefaultUpdatesRepository(pushUpdatesDao = dao)

            val saved =
                repository.saveIncomingUpdate(
                    update =
                        ParsedPushUpdate(
                            remoteEventId = "evt-1",
                            linkUpdateId = 1L,
                            updateOwner = "bot",
                            creationDate = "2026-05-13T20:00:00Z",
                            linkUrl = "https://example.com/post",
                            title = "Title",
                            content = "Body",
                        ),
                    receivedAtEpochMs = 5000L,
                )

            assertTrue(saved)
            val inserted = requireNotNull(dao.lastInserted)
            assertEquals("evt-1", inserted.remoteEventId)
            assertEquals(5000L, inserted.receivedAtEpochMs)
            assertFalse(inserted.isRead)
        }
    }

    @Test
    fun saveIncomingUpdate_whenDuplicateInsertIgnored_returnsFalse() {
        runTest {
            val dao = FakePushUpdatesDao(insertResult = -1L)
            val repository = DefaultUpdatesRepository(pushUpdatesDao = dao)

            val saved =
                repository.saveIncomingUpdate(
                    update =
                        ParsedPushUpdate(
                            remoteEventId = "evt-dup",
                            linkUpdateId = null,
                            updateOwner = "unknown",
                            creationDate = "",
                            linkUrl = "https://example.com/post",
                            title = "Title",
                            content = "Body",
                        ),
                    receivedAtEpochMs = 9000L,
                )

            assertFalse(saved)
        }
    }

    @Test
    fun observeUpdates_mapsEntitiesToDomain() {
        runTest {
            val dao =
                FakePushUpdatesDao(
                    insertResult = 1L,
                    initial =
                        listOf(
                            PushUpdateEntity(
                                id = 7L,
                                remoteEventId = "evt-7",
                                linkUrl = "https://example.com/x",
                                title = "Hello",
                                content = "Payload body",
                                receivedAtEpochMs = 77L,
                                isRead = true,
                            ),
                        ),
                )
            val repository = DefaultUpdatesRepository(pushUpdatesDao = dao)

            val updates = repository.observeUpdates().first()

            assertEquals(1, updates.size)
            assertEquals(7L, updates.first().id)
            assertEquals("evt-7", updates.first().remoteEventId)
            assertTrue(updates.first().isRead)
        }
    }

    @Test
    fun markAsRead_whenDaoUpdatesRow_returnsTrue() {
        runTest {
            val dao = FakePushUpdatesDao(insertResult = 1L, markAsReadResult = 1)
            val repository = DefaultUpdatesRepository(pushUpdatesDao = dao)

            val result = repository.markAsRead(updateId = 7L)

            assertTrue(result)
            assertEquals(7L, dao.lastMarkedUpdateId)
        }
    }

    @Test
    fun markAsRead_whenDaoUpdatesNothing_returnsFalse() {
        runTest {
            val dao = FakePushUpdatesDao(insertResult = 1L, markAsReadResult = 0)
            val repository = DefaultUpdatesRepository(pushUpdatesDao = dao)

            val result = repository.markAsRead(updateId = 77L)

            assertFalse(result)
            assertEquals(77L, dao.lastMarkedUpdateId)
        }
    }

    private class FakePushUpdatesDao(
        private val insertResult: Long,
        private val markAsReadResult: Int = 1,
        initial: List<PushUpdateEntity> = emptyList(),
    ) : PushUpdatesDao {
        private val data = MutableStateFlow(initial)
        var lastInserted: PushUpdateEntity? = null
            private set
        var lastMarkedUpdateId: Long? = null
            private set

        override fun observeAll(): Flow<List<PushUpdateEntity>> = data

        override suspend fun insert(update: PushUpdateEntity): Long {
            lastInserted = update
            if (insertResult != -1L) {
                data.value = listOf(update.copy(id = insertResult)) + data.value
            }
            return insertResult
        }

        override suspend fun markAsRead(updateId: Long): Int {
            lastMarkedUpdateId = updateId
            if (markAsReadResult > 0) {
                data.value =
                    data.value.map { entity ->
                        if (entity.id == updateId) entity.copy(isRead = true) else entity
                    }
            }
            return markAsReadResult
        }

        override suspend fun clearAll() {
            data.value = emptyList()
        }
    }
}
