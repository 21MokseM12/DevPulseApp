package com.devpulse.app.push

import android.util.Log
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DevPulseFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenStore: PushTokenStore

    @Inject
    lateinit var pushMessageHandler: PushMessageHandler

    @Inject
    lateinit var pushNotifier: PushNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (token.isBlank()) return
        scope.launch {
            pushTokenStore.saveToken(token)
        }
        Log.d(LOG_TAG, "FCM token обновлен")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            processIncomingMessage(message)
        }
    }

    internal suspend fun processIncomingMessage(
        message: RemoteMessage,
        receivedAtEpochMs: Long = System.currentTimeMillis(),
    ): PushHandleOutcome {
        return processIncomingPayload(
            payload = message.data,
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            messageId = message.messageId,
            receivedAtEpochMs = receivedAtEpochMs,
        )
    }

    internal suspend fun processIncomingPayload(
        payload: Map<String, String>,
        notificationTitle: String?,
        notificationBody: String?,
        messageId: String?,
        receivedAtEpochMs: Long,
    ): PushHandleOutcome {
        val outcome =
            processIncomingPush(
                payload = payload,
                notificationTitle = notificationTitle,
                notificationBody = notificationBody,
                messageId = messageId,
                receivedAtEpochMs = receivedAtEpochMs,
                pushMessageHandler = pushMessageHandler,
                pushNotifier = pushNotifier,
            )
        Log.d(
            LOG_TAG,
            "Получен push: messageId=$messageId, result=${outcome.result}, " +
                "quietSuppressed=${outcome.suppressedByQuietHours}, dataKeys=${payload.keys}",
        )
        return outcome
    }

    private companion object {
        const val LOG_TAG = "DevPulseFcmService"
    }
}

internal suspend fun processIncomingPush(
    payload: Map<String, String>,
    notificationTitle: String?,
    notificationBody: String?,
    messageId: String?,
    receivedAtEpochMs: Long,
    pushMessageHandler: PushMessageHandler,
    pushNotifier: PushNotifier,
): PushHandleOutcome {
    val outcome =
        pushMessageHandler.handle(
            payload = payload,
            notificationTitle = notificationTitle,
            notificationBody = notificationBody,
            messageId = messageId,
            receivedAtEpochMs = receivedAtEpochMs,
        )
    if (outcome.result == PushHandleResult.Saved && outcome.update != null && outcome.shouldShowSystemNotification) {
        pushNotifier.showUpdateNotification(
            update = outcome.update,
            presentationMode = outcome.presentationMode,
            digestMode = outcome.digestMode,
        )
    }
    return outcome
}
