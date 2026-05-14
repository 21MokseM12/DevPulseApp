package com.devpulse.app.push.e2e

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.DigestSummaryPayload
import com.devpulse.app.push.DigestUpdateAggregator
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.push.PushMessageHandler
import com.devpulse.app.push.PushNotifier
import com.devpulse.app.push.PushPayloadParser
import com.devpulse.app.push.QuietHoursPolicyEvaluator
import com.devpulse.app.push.processIncomingPush
import com.devpulse.app.push.runDigestWorkerCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestPipelineNoDuplicatesE2eTest {
    @Test
    fun endToEnd_digestMode_enabled_deliversDigestWithoutInstantDuplicates() =
        runTest {
            val updatesRepository = InMemoryUpdatesRepository()
            val preferencesStore =
                MutablePreferencesStore(
                    NotificationPreferences(
                        enabled = true,
                        digestMode = NotificationDigestMode.Hourly,
                        digestLastProcessedAtEpochMs = 100L,
                    ),
                )
            val pushNotifier = RecordingNotifier()
            val handler =
                PushMessageHandler(
                    payloadParser = PushPayloadParser(),
                    updatesRepository = updatesRepository,
                    notificationPreferencesStore = preferencesStore,
                    quietHoursPolicyEvaluator = QuietHoursPolicyEvaluator(),
                )

            processIncomingPush(
                payload =
                    mapOf(
                        "event_id" to "evt-e2e-1",
                        "url" to "https://example.com/e2e/1",
                        "content" to "Body",
                    ),
                notificationTitle = "Digest update",
                notificationBody = null,
                messageId = "msg-e2e-1",
                receivedAtEpochMs = 120L,
                pushMessageHandler = handler,
                pushNotifier = pushNotifier,
            )
            runDigestWorkerCycle(
                updatesRepository = updatesRepository,
                notificationPreferencesStore = preferencesStore,
                digestUpdateAggregator = DigestUpdateAggregator(),
                pushNotifier = pushNotifier,
                nowEpochMs = 200L,
            )
            runDigestWorkerCycle(
                updatesRepository = updatesRepository,
                notificationPreferencesStore = preferencesStore,
                digestUpdateAggregator = DigestUpdateAggregator(),
                pushNotifier = pushNotifier,
                nowEpochMs = 300L,
            )

            assertTrue(pushNotifier.instantCalls.isEmpty())
            assertEquals(1, pushNotifier.digestCalls.size)
            assertEquals(1, pushNotifier.digestCalls.single().updatesCount)
            assertEquals(300L, preferencesStore.preferences.digestLastProcessedAtEpochMs)
        }

    private class RecordingNotifier : PushNotifier {
        val instantCalls = mutableListOf<ParsedPushUpdate>()
        val digestCalls = mutableListOf<DigestSummaryPayload>()

        override fun showUpdateNotification(
            update: ParsedPushUpdate,
            presentationMode: NotificationPresentationMode,
            digestMode: NotificationDigestMode?,
        ) {
            instantCalls += update
        }

        override fun showDigestNotification(
            summary: DigestSummaryPayload,
            digestMode: NotificationDigestMode,
        ) {
            digestCalls += summary
        }
    }

    private class InMemoryUpdatesRepository : UpdatesRepository {
        private val updates = linkedMapOf<String, UpdateEvent>()

        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(updates.values.toList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            val id = update.remoteEventId ?: return false
            if (updates.containsKey(id)) return false
            updates[id] =
                UpdateEvent(
                    id = updates.size.toLong() + 1L,
                    remoteEventId = id,
                    linkUrl = update.linkUrl,
                    title = update.title,
                    content = update.content,
                    receivedAtEpochMs = receivedAtEpochMs,
                    isRead = false,
                    source = update.updateOwner,
                )
            return true
        }

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class MutablePreferencesStore(
        initialPreferences: NotificationPreferences,
    ) : NotificationPreferencesStore {
        var preferences: NotificationPreferences = initialPreferences

        override fun observePreferences(): Flow<NotificationPreferences> = flowOf(preferences)

        override suspend fun getPreferences(): NotificationPreferences = preferences

        override suspend fun setEnabled(enabled: Boolean) {
            preferences = preferences.copy(enabled = enabled)
        }

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) {
            preferences = preferences.copy(presentationMode = mode)
        }

        override suspend fun setDigestMode(mode: NotificationDigestMode?) {
            preferences = preferences.copy(digestMode = mode)
        }

        override suspend fun setDigestLastProcessedAt(epochMs: Long) {
            preferences = preferences.copy(digestLastProcessedAtEpochMs = epochMs)
        }

        override suspend fun setQuietHoursPolicy(policy: QuietHoursPolicy) {
            preferences = preferences.copy(quietHoursPolicy = policy)
        }

        override suspend fun reset() {
            preferences = NotificationPreferences()
        }
    }
}
