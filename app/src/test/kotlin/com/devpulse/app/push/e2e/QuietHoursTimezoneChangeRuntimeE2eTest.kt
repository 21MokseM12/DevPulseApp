package com.devpulse.app.push.e2e

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.push.PushHandleResult
import com.devpulse.app.push.PushMessageHandler
import com.devpulse.app.push.PushPayloadParser
import com.devpulse.app.push.QuietHoursPolicyEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class QuietHoursTimezoneChangeRuntimeE2eTest {
    @Test
    fun runtime_timezoneChange_deviceMode_recalculatesQuietHoursSuppression() =
        runTest {
            val previousDefaultTimezone = TimeZone.getDefault()
            try {
                val handler = createHandlerWithPolicy(timezoneMode = QuietHoursTimezoneMode.Device)
                val payload = validPayload("evt-device-tz")
                val receivedAt = java.time.Instant.parse("2026-05-15T20:30:00Z").toEpochMilli()

                TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
                val outcomeUtc =
                    handler.handle(
                        payload = payload,
                        notificationTitle = "Title",
                        notificationBody = "Body",
                        messageId = "msg-device-utc",
                        receivedAtEpochMs = receivedAt,
                    )

                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
                val outcomeTokyo =
                    handler.handle(
                        payload = validPayload("evt-device-tz-2"),
                        notificationTitle = "Title",
                        notificationBody = "Body",
                        messageId = "msg-device-tokyo",
                        receivedAtEpochMs = receivedAt,
                    )

                assertTrue(outcomeUtc.result == PushHandleResult.Saved)
                assertTrue(outcomeTokyo.result == PushHandleResult.Saved)
                assertFalse(outcomeUtc.suppressedByQuietHours)
                assertTrue(outcomeTokyo.suppressedByQuietHours)
            } finally {
                TimeZone.setDefault(previousDefaultTimezone)
            }
        }

    @Test
    fun runtime_timezoneChange_fixedMode_keepsSuppressionStable() =
        runTest {
            val previousDefaultTimezone = TimeZone.getDefault()
            try {
                val handler = createHandlerWithPolicy(timezoneMode = QuietHoursTimezoneMode.Fixed)
                val payload = validPayload("evt-fixed-tz")
                val receivedAt = java.time.Instant.parse("2026-05-15T20:30:00Z").toEpochMilli()

                TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
                val outcomeUtc =
                    handler.handle(
                        payload = payload,
                        notificationTitle = "Title",
                        notificationBody = "Body",
                        messageId = "msg-fixed-utc",
                        receivedAtEpochMs = receivedAt,
                    )

                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
                val outcomeTokyo =
                    handler.handle(
                        payload = validPayload("evt-fixed-tz-2"),
                        notificationTitle = "Title",
                        notificationBody = "Body",
                        messageId = "msg-fixed-tokyo",
                        receivedAtEpochMs = receivedAt,
                    )

                assertTrue(outcomeUtc.suppressedByQuietHours)
                assertTrue(outcomeTokyo.suppressedByQuietHours)
            } finally {
                TimeZone.setDefault(previousDefaultTimezone)
            }
        }

    private fun createHandlerWithPolicy(timezoneMode: QuietHoursTimezoneMode): PushMessageHandler {
        val policy =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 7 * 60,
                timezoneMode = timezoneMode,
                fixedZoneId = "Asia/Tokyo",
            )
        val preferences = NotificationPreferences(enabled = true, quietHoursPolicy = policy)
        return PushMessageHandler(
            payloadParser = PushPayloadParser(),
            updatesRepository = InMemoryUpdatesRepository(),
            notificationPreferencesStore = StaticPreferencesStore(preferences),
            quietHoursPolicyEvaluator = QuietHoursPolicyEvaluator(),
        )
    }

    private fun validPayload(eventId: String): Map<String, String> =
        mapOf(
            "event_id" to eventId,
            "url" to "https://example.com/runtime-timezone",
            "content" to "Runtime timezone change",
        )

    private class InMemoryUpdatesRepository : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = flowOf(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class StaticPreferencesStore(
        private val preferences: NotificationPreferences,
    ) : NotificationPreferencesStore {
        override fun observePreferences(): Flow<NotificationPreferences> = flowOf(preferences)

        override suspend fun getPreferences(): NotificationPreferences = preferences

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) = Unit

        override suspend fun setDigestMode(mode: NotificationDigestMode?) = Unit

        override suspend fun setDigestLastProcessedAt(epochMs: Long) = Unit

        override suspend fun setQuietHoursPolicy(policy: QuietHoursPolicy) = Unit

        override suspend fun reset() = Unit
    }
}
