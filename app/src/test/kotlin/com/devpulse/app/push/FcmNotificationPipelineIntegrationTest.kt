package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FcmNotificationPipelineIntegrationTest {
    @Test
    fun processIncomingPayload_enabledPreferences_deliversSystemNotification() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler = createHandler(repository, StaticPreferencesStore(NotificationPreferences()))

            val outcome =
                processIncomingPush(
                    payload = validPayload(eventId = "evt-1"),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-1",
                    receivedAtEpochMs = 100L,
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            assertEquals(1, repository.saveCalls)
            assertEquals(1, notifier.calls.size)
            assertEquals(null, notifier.calls.single().digestMode)
        }
    }

    @Test
    fun processIncomingPayload_disabledInAppPreference_savesButSkipsSystemNotification() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler =
                createHandler(
                    repository,
                    StaticPreferencesStore(NotificationPreferences(enabled = false)),
                )

            val outcome =
                processIncomingPush(
                    payload = validPayload(eventId = "evt-2"),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-2",
                    receivedAtEpochMs = 200L,
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            assertEquals(1, repository.saveCalls)
            assertEquals(0, notifier.calls.size)
        }
    }

    @Test
    fun processIncomingPayload_preferencesReadFailure_savesEventAndUsesFailClosed() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler =
                createHandler(
                    repository,
                    StaticPreferencesStore(NotificationPreferences(), failOnGet = true),
                )

            val outcome =
                processIncomingPush(
                    payload = validPayload(eventId = "evt-3"),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-3",
                    receivedAtEpochMs = 300L,
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            assertEquals(1, repository.saveCalls)
            assertEquals(false, outcome.shouldShowSystemNotification)
            assertEquals(0, notifier.calls.size)
        }
    }

    @Test
    fun processIncomingPayload_digestMode_routesDigestToNotifier() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler =
                createHandler(
                    repository,
                    StaticPreferencesStore(
                        NotificationPreferences(
                            enabled = true,
                            presentationMode = NotificationPresentationMode.Compact,
                            digestMode = NotificationDigestMode.Daily,
                        ),
                    ),
                )

            val outcome =
                processIncomingPush(
                    payload = validPayload(eventId = "evt-4"),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-4",
                    receivedAtEpochMs = 400L,
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            assertEquals(0, notifier.calls.size)
        }
    }

    @Test
    fun processIncomingPayload_invalidPayload_skipsRepositoryAndNotifier() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler = createHandler(repository, StaticPreferencesStore(NotificationPreferences()))

            val outcome =
                processIncomingPush(
                    payload = mapOf("url" to "not-a-valid-url"),
                    notificationTitle = null,
                    notificationBody = null,
                    messageId = "msg-invalid",
                    receivedAtEpochMs = 500L,
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.IgnoredInvalidPayload, outcome.result)
            assertEquals(0, repository.saveCalls)
            assertEquals(0, notifier.calls.size)
            assertNull(outcome.update)
        }
    }

    @Test
    fun processIncomingPayload_quietHoursEnabled_savesButSuppressesNotifier() {
        runTest {
            val repository = RecordingUpdatesRepository()
            val notifier = RecordingNotifier()
            val handler =
                createHandler(
                    repository,
                    StaticPreferencesStore(
                        NotificationPreferences(
                            enabled = true,
                            quietHoursPolicy =
                                QuietHoursPolicy(
                                    enabled = true,
                                    fromMinutes = 22 * 60,
                                    toMinutes = 7 * 60,
                                ),
                        ),
                    ),
                )

            val outcome =
                processIncomingPush(
                    payload = validPayload(eventId = "evt-5"),
                    notificationTitle = "Title",
                    notificationBody = null,
                    messageId = "msg-5",
                    receivedAtEpochMs = java.time.Instant.parse("2026-05-15T03:00:00Z").toEpochMilli(),
                    pushMessageHandler = handler,
                    pushNotifier = notifier,
                )

            assertEquals(PushHandleResult.Saved, outcome.result)
            assertEquals(true, outcome.suppressedByQuietHours)
            assertEquals(1, repository.saveCalls)
            assertEquals(0, notifier.calls.size)
        }
    }

    private fun createHandler(
        repository: RecordingUpdatesRepository,
        preferencesStore: NotificationPreferencesStore,
    ): PushMessageHandler =
        PushMessageHandler(
            payloadParser = PushPayloadParser(),
            updatesRepository = repository,
            notificationPreferencesStore = preferencesStore,
            quietHoursPolicyEvaluator = QuietHoursPolicyEvaluator(),
        )

    private fun validPayload(eventId: String): Map<String, String> =
        mapOf(
            "event_id" to eventId,
            "url" to "https://example.com/post",
            "content" to "Body",
        )

    private class RecordingUpdatesRepository : UpdatesRepository {
        var saveCalls: Int = 0
            private set
        var lastUpdate: ParsedPushUpdate? = null
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            saveCalls += 1
            lastUpdate = update
            return true
        }

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private data class NotificationCall(
        val update: ParsedPushUpdate,
        val presentationMode: NotificationPresentationMode,
        val digestMode: NotificationDigestMode?,
    )

    private class RecordingNotifier : PushNotifier {
        val calls: MutableList<NotificationCall> = mutableListOf()

        override fun showUpdateNotification(
            update: ParsedPushUpdate,
            presentationMode: NotificationPresentationMode,
            digestMode: NotificationDigestMode?,
        ) {
            calls += NotificationCall(update, presentationMode, digestMode)
        }

        override fun showDigestNotification(
            summary: DigestSummaryPayload,
            digestMode: NotificationDigestMode,
        ) = Unit
    }

    private class StaticPreferencesStore(
        private val preferences: NotificationPreferences,
        private val failOnGet: Boolean = false,
    ) : NotificationPreferencesStore {
        override fun observePreferences(): Flow<NotificationPreferences> = flowOf(preferences)

        override suspend fun getPreferences(): NotificationPreferences {
            if (failOnGet) error("read failed")
            return preferences
        }

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) = Unit

        override suspend fun setDigestMode(mode: NotificationDigestMode?) = Unit

        override suspend fun setDigestLastProcessedAt(epochMs: Long) = Unit

        override suspend fun setQuietHoursPolicy(policy: com.devpulse.app.data.local.preferences.QuietHoursPolicy) =
            Unit

        override suspend fun reset() = Unit
    }
}
