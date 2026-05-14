package com.devpulse.app.push

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
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
)

@Singleton
class PushMessageHandler
    @Inject
    constructor(
        private val payloadParser: PushPayloadParser,
        private val updatesRepository: UpdatesRepository,
        private val notificationPreferencesStore: NotificationPreferencesStore,
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
                val preferences = notificationPreferencesStore.getPreferences()
                PushHandleOutcome(
                    result = PushHandleResult.Saved,
                    update = parsed,
                    shouldShowSystemNotification = preferences.enabled,
                    presentationMode = preferences.presentationMode,
                )
            } else {
                PushHandleOutcome(
                    result = PushHandleResult.IgnoredDuplicate,
                    update = parsed,
                )
            }
        }
    }
