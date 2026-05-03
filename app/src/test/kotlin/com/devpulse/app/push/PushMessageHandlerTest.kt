package com.devpulse.app.push

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushMessageHandlerTest {
    private val parser = PushPayloadParser()

    @Test
    fun handle_validPayload_savesUpdate() {
        runTest {
            val repository = FakeUpdatesRepository(saveResult = true)
            val handler = PushMessageHandler(payloadParser = parser, updatesRepository = repository)

            val result =
                handler.handle(
                    payload =
                        mapOf(
                            "event_id" to "evt-15",
                            "url" to "https://example.com/news",
                            "content" to "News body",
                        ),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-1",
                    receivedAtEpochMs = 1234L,
                )

            assertEquals(PushHandleResult.Saved, result.result)
            assertNotNull(result.update)
            assertEquals(1, repository.saveCalls)
            assertNotNull(repository.lastUpdate)
            assertEquals(1234L, repository.lastReceivedAt)
        }
    }

    @Test
    fun handle_invalidPayload_doesNotSaveAndReturnsIgnoredInvalid() {
        runTest {
            val repository = FakeUpdatesRepository(saveResult = true)
            val handler = PushMessageHandler(payloadParser = parser, updatesRepository = repository)

            val result =
                handler.handle(
                    payload = mapOf("title" to "No content and no url"),
                    notificationTitle = null,
                    notificationBody = null,
                    messageId = "msg-2",
                    receivedAtEpochMs = 100L,
                )

            assertEquals(PushHandleResult.IgnoredInvalidPayload, result.result)
            assertNull(result.update)
            assertEquals(0, repository.saveCalls)
            assertNull(repository.lastUpdate)
        }
    }

    private class FakeUpdatesRepository(
        private val saveResult: Boolean,
    ) : UpdatesRepository {
        var saveCalls: Int = 0
            private set
        var lastUpdate: ParsedPushUpdate? = null
            private set
        var lastReceivedAt: Long? = null
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            saveCalls += 1
            lastUpdate = update
            lastReceivedAt = receivedAtEpochMs
            return saveResult
        }

        override suspend fun markAsRead(updateId: Long): Boolean = false
    }
}
