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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (token.isBlank()) return
        scope.launch {
            pushTokenStore.saveToken(token)
        }
        Log.d(LOG_TAG, "FCM token обновлен")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Full payload parsing and user-facing notification will be implemented in E-2/E-3.
        Log.d(
            LOG_TAG,
            "Получен push: messageId=${message.messageId}, dataKeys=${message.data.keys}",
        )
    }

    private companion object {
        const val LOG_TAG = "DevPulseFcmService"
    }
}
