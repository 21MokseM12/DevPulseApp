package com.devpulse.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushInitializer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val pushTokenStore: PushTokenStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun initialize() {
            ensureNotificationChannel()
            requestAndStoreToken()
        }

        private fun ensureNotificationChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel =
                NotificationChannel(
                    PushNotificationChannels.UPDATES_CHANNEL_ID,
                    PushNotificationChannels.UPDATES_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = PushNotificationChannels.UPDATES_CHANNEL_DESCRIPTION
                }
            manager.createNotificationChannel(channel)
        }

        private fun requestAndStoreToken() {
            val tokenTask =
                runCatching { FirebaseMessaging.getInstance().token }.getOrElse { throwable ->
                    Log.w(LOG_TAG, "Firebase Messaging не настроен: token недоступен", throwable)
                    return
                }

            tokenTask.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(LOG_TAG, "Не удалось получить FCM token при старте", task.exception)
                    return@addOnCompleteListener
                }

                val token = task.result.orEmpty()
                if (token.isBlank()) return@addOnCompleteListener

                scope.launch {
                    pushTokenStore.saveToken(token)
                }
                Log.d(LOG_TAG, "FCM token получен и сохранен")
            }
        }

        private companion object {
            const val LOG_TAG = "PushInitializer"
        }
    }
