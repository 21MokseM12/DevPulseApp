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

class PushHandlingIntegrationTest {
    @Test
    fun handle_payloadWithoutContent_usesFallbackAndSavesUpdate() {
        runTest {
            val repository = CapturingUpdatesRepository()
            val handler =
                PushMessageHandler(
                    payloadParser = PushPayloadParser(),
                    updatesRepository = repository,
                )

            val outcome =
                handler.handle(
                    payload = mapOf("url" to "https://example.com/post"),
                    notificationTitle = "Заголовок",
                    notificationBody = " ",
                    messageId = "msg-42",
                    receivedAtEpochMs = 4200L,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            val saved = requireNotNull(repository.lastUpdate)
            assertEquals("https://example.com/post", saved.linkUrl)
            assertEquals("Заголовок", saved.title)
            assertEquals("Проверьте новые изменения по отслеживаемой ссылке.", saved.content)
            assertEquals("msg-42", saved.remoteEventId)
            assertNotNull(outcome.update)
        }
    }

    @Test
    fun handle_payloadWithoutLinkUpdateFields_usesContractDefaults() {
        runTest {
            val repository = CapturingUpdatesRepository()
            val handler =
                PushMessageHandler(
                    payloadParser = PushPayloadParser(),
                    updatesRepository = repository,
                )

            val outcome =
                handler.handle(
                    payload =
                        mapOf(
                            "url" to "https://example.com/contract",
                            "content" to "Update body",
                        ),
                    notificationTitle = null,
                    notificationBody = null,
                    messageId = "evt-fallback",
                    receivedAtEpochMs = 100L,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            val saved = requireNotNull(repository.lastUpdate)
            assertEquals("evt-fallback", saved.remoteEventId)
            assertEquals(null, saved.linkUpdateId)
            assertEquals("unknown", saved.updateOwner)
            assertEquals("", saved.creationDate)
        }
    }

    @Test
    fun handle_malformedPayload_isIgnoredWithoutSaving() {
        runTest {
            val repository = CapturingUpdatesRepository()
            val handler =
                PushMessageHandler(
                    payloadParser = PushPayloadParser(),
                    updatesRepository = repository,
                )

            val outcome =
                handler.handle(
                    payload =
                        mapOf(
                            "url" to "not-a-valid-url",
                            "content" to "Body",
                        ),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-malformed",
                    receivedAtEpochMs = 500L,
                )

            assertEquals(PushHandleResult.IgnoredInvalidPayload, outcome.result)
            assertNull(outcome.update)
            assertNull(repository.lastUpdate)
        }
    }

    private class CapturingUpdatesRepository : UpdatesRepository {
        var lastUpdate: ParsedPushUpdate? = null
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            lastUpdate = update
            return true
        }

        override suspend fun markAsRead(updateId: Long): Boolean = false

        override suspend fun clearUpdates() = Unit
    }
}
