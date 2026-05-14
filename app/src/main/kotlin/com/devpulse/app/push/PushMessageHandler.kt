package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPreferences
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import com.devpulse.app.domain.repository.UpdatesRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class PushHandleResult {
    Saved,
    IgnoredInvalidPayload,
    IgnoredDuplicate,
}

data class PushHandleOutcome(
    val result: PushHandleResult,
    val update: ParsedPushUpdate? = null,
    val shouldShowSystemNotification: Boolean = false,
    val suppressedByQuietHours: Boolean = false,
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
    val digestMode: NotificationDigestMode? = null,
)

@Singleton
class PushMessageHandler
    @Inject
    constructor(
        private val payloadParser: PushPayloadParser,
        private val updatesRepository: UpdatesRepository,
        private val notificationPreferencesStore: NotificationPreferencesStore,
        private val quietHoursPolicyEvaluator: QuietHoursPolicyEvaluator,
    ) {
        suspend fun handle(
            payload: Map<String, String>,
            notificationTitle: String?,
            notificationBody: String?,
            messageId: String?,
            receivedAtEpochMs: Long,
        ): PushHandleOutcome {
            val parsed =
                payloadParser.parse(
                    payload = payload,
                    notificationTitle = notificationTitle,
                    notificationBody = notificationBody,
                    fallbackMessageId = messageId,
                ) ?: return PushHandleOutcome(result = PushHandleResult.IgnoredInvalidPayload)

            val wasSaved =
                updatesRepository.saveIncomingUpdate(
                    update = parsed,
                    receivedAtEpochMs = receivedAtEpochMs,
                )
            return if (wasSaved) {
                val preferences =
                    runCatching { notificationPreferencesStore.getPreferences() }
                        .getOrDefault(
                            FALLBACK_PREFERENCES_ON_ERROR,
                        )
                val suppressedByQuietHours =
                    quietHoursPolicyEvaluator.shouldSuppressNotification(
                        schedule = preferences.quietHoursPolicy,
                        update = parsed,
                        now = java.time.Instant.ofEpochMilli(receivedAtEpochMs),
                    )
                PushHandleOutcome(
                    result = PushHandleResult.Saved,
                    update = parsed,
                    shouldShowSystemNotification = preferences.enabled && !suppressedByQuietHours,
                    suppressedByQuietHours = suppressedByQuietHours,
                    presentationMode = preferences.presentationMode,
                    digestMode = preferences.digestMode,
                )
            } else {
                PushHandleOutcome(
                    result = PushHandleResult.IgnoredDuplicate,
                    update = parsed,
                )
            }
        }

        private companion object {
            val FALLBACK_PREFERENCES_ON_ERROR =
                NotificationPreferences(
                    enabled = false,
                    presentationMode = NotificationPresentationMode.Detailed,
                    digestMode = null,
                )
        }
    }
