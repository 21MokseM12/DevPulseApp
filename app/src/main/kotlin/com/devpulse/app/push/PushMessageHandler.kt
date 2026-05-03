package com.devpulse.app.push

import com.devpulse.app.domain.repository.UpdatesRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class PushHandleResult {
    Saved,
    IgnoredInvalidPayload,
    IgnoredDuplicate,
}

@Singleton
class PushMessageHandler
    @Inject
    constructor(
        private val payloadParser: PushPayloadParser,
        private val updatesRepository: UpdatesRepository,
    ) {
        suspend fun handle(
            payload: Map<String, String>,
            notificationTitle: String?,
            notificationBody: String?,
            messageId: String?,
            receivedAtEpochMs: Long,
        ): PushHandleResult {
            val parsed =
                payloadParser.parse(
                    payload = payload,
                    notificationTitle = notificationTitle,
                    notificationBody = notificationBody,
                    fallbackMessageId = messageId,
                ) ?: return PushHandleResult.IgnoredInvalidPayload

            val wasSaved =
                updatesRepository.saveIncomingUpdate(
                    update = parsed,
                    receivedAtEpochMs = receivedAtEpochMs,
                )
            return if (wasSaved) PushHandleResult.Saved else PushHandleResult.IgnoredDuplicate
        }
    }
