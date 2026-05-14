package com.devpulse.app.push

import androidx.work.ListenableWorker
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
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestWorkerDoWorkTest {
    @Test
    fun doWork_fullScenario_aggregatesNotifiesAndUpdatesLastProcessedAt() =
        runTest {
            val updatesRepository =
                FakeUpdatesRepository(
                    updates =
                        listOf(
                            update(id = 1L, source = "github", receivedAt = 110L),
                            update(id = 2L, source = "jira", receivedAt = 120L),
                        ),
                )
            val preferencesStore =
                FakePreferencesStore(
                    NotificationPreferences(
                        enabled = true,
                        digestMode = NotificationDigestMode.Hourly,
                        digestLastProcessedAtEpochMs = 100L,
                    ),
                )
            val notifier = RecordingDigestNotifier()

            val result =
                runDigestWorkerCycle(
                    updatesRepository = updatesRepository,
                    notificationPreferencesStore = preferencesStore,
                    digestUpdateAggregator = DigestUpdateAggregator(),
                    pushNotifier = notifier,
                    nowEpochMs = 150L,
                )

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, notifier.digestCalls.size)
            assertEquals(2, notifier.digestCalls.single().summary.updatesCount)
            assertEquals(150L, preferencesStore.lastProcessedAt)
        }

    @Test
    fun doWork_withoutUpdates_stillUpdatesLastProcessedAtWithoutNotification() =
        runTest {
            val updatesRepository =
                FakeUpdatesRepository(
                    updates =
                        listOf(
                            update(id = 1L, source = "github", receivedAt = 90L),
                        ),
                )
            val preferencesStore =
                FakePreferencesStore(
                    NotificationPreferences(
                        enabled = true,
                        digestMode = NotificationDigestMode.Daily,
                        digestLastProcessedAtEpochMs = 100L,
                    ),
                )
            val notifier = RecordingDigestNotifier()

            val result =
                runDigestWorkerCycle(
                    updatesRepository = updatesRepository,
                    notificationPreferencesStore = preferencesStore,
                    digestUpdateAggregator = DigestUpdateAggregator(),
                    pushNotifier = notifier,
                    nowEpochMs = 200L,
                )

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue(notifier.digestCalls.isEmpty())
            assertEquals(200L, preferencesStore.lastProcessedAt)
        }

    private fun update(
        id: Long,
        source: String,
        receivedAt: Long,
    ): UpdateEvent {
        return UpdateEvent(
            id = id,
            remoteEventId = id.toString(),
            linkUrl = "https://example.com/$id",
            title = "title-$id",
            content = "content-$id",
            receivedAtEpochMs = receivedAt,
            isRead = false,
            source = source,
        )
    }

    private class RecordingDigestNotifier : PushNotifier {
        val digestCalls = mutableListOf<DigestCall>()

        override fun showUpdateNotification(
            update: ParsedPushUpdate,
            presentationMode: NotificationPresentationMode,
            digestMode: NotificationDigestMode?,
        ) = Unit

        override fun showDigestNotification(
            summary: DigestSummaryPayload,
            digestMode: NotificationDigestMode,
        ) {
            digestCalls += DigestCall(summary, digestMode)
        }
    }

    private data class DigestCall(
        val summary: DigestSummaryPayload,
        val digestMode: NotificationDigestMode,
    )

    private class FakeUpdatesRepository(
        private val updates: List<UpdateEvent>,
    ) : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(updates)

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class FakePreferencesStore(
        private val preferences: NotificationPreferences,
    ) : NotificationPreferencesStore {
        var lastProcessedAt: Long? = null
            private set

        override fun observePreferences(): Flow<NotificationPreferences> = flowOf(preferences)

        override suspend fun getPreferences(): NotificationPreferences = preferences

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) = Unit

        override suspend fun setDigestMode(mode: NotificationDigestMode?) = Unit

        override suspend fun setDigestLastProcessedAt(epochMs: Long) {
            lastProcessedAt = epochMs
        }

        override suspend fun setQuietHoursPolicy(policy: QuietHoursPolicy) = Unit

        override suspend fun reset() = Unit
    }
}
