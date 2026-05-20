package com.devpulse.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.devpulse.app.BuildConfig
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushInitializer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val pushTokenStore: PushTokenStore,
        private val sessionStore: SessionStore,
        private val notificationPreferencesStore: NotificationPreferencesStore,
        private val digestScheduler: DigestScheduler,
        private val pushTokenSyncOrchestrator: PushTokenSyncCoordinator,
        private val analyticsLogger: PushAnalyticsTracker,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun initialize() {
            ensureNotificationChannel()
            syncDigestScheduler()
            observeTokenAndSession()
            syncPendingTokenState()
            requestAndStoreToken()
        }

        private fun ensureNotificationChannel() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channelConfig = updatesNotificationChannelConfig()
            val channel =
                NotificationChannel(
                    channelConfig.id,
                    channelConfig.name,
                    channelConfig.importance,
                ).apply {
                    description = channelConfig.description
                }
            manager.createNotificationChannel(channel)
        }

        private fun requestAndStoreToken() {
            if (!shouldRequestFcmToken(BuildConfig.FIREBASE_CONFIGURED)) {
                Log.i(LOG_TAG, "FCM отключен: google-services.json не найден")
                return
            }
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
                if (!shouldSaveToken(task.isSuccessful, task.result)) return@addOnCompleteListener
                val token = task.result.orEmpty().trim()

                scope.launch {
                    pushTokenStore.saveToken(token)
                    pushTokenSyncOrchestrator.queueRegister(
                        token = token,
                        reason = "app_start_token_fetch",
                    )
                }
                analyticsLogger.tokenRefresh(source = "app_start_token_fetch")
                Log.d(LOG_TAG, "fcm_token_received source=app_start")
            }
        }

        private fun observeTokenAndSession() {
            scope.launch {
                combine(
                    pushTokenStore.observeToken(),
                    sessionStore.observeClientLogin(),
                ) { token, login -> token to login }
                    .distinctUntilChanged()
                    .collect { (token, login) ->
                        if (token.isNullOrBlank()) return@collect
                        pushTokenSyncOrchestrator.queueRegister(
                            token = token,
                            reason = if (login.isNullOrBlank()) "token_changed" else "session_or_token_changed",
                        )
                    }
            }
        }

        private fun syncPendingTokenState() {
            scope.launch {
                pushTokenSyncOrchestrator.syncPending(reason = "app_start_pending_sync")
            }
        }

        private fun syncDigestScheduler() {
            scope.launch {
                runCatching { notificationPreferencesStore.getPreferences() }
                    .onSuccess { preferences -> digestScheduler.sync(preferences) }
                    .onFailure { error ->
                        Log.w(LOG_TAG, "Не удалось синхронизировать digest scheduler", error)
                    }
            }
        }

        private companion object {
            const val LOG_TAG = "PushInitializer"
        }
    }

internal fun shouldSaveToken(
    taskSuccessful: Boolean,
    token: String?,
): Boolean {
    return taskSuccessful && !token.isNullOrBlank()
}

internal fun shouldRequestFcmToken(firebaseConfigured: Boolean): Boolean = firebaseConfigured

internal data class NotificationChannelConfig(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
)

internal fun updatesNotificationChannelConfig(): NotificationChannelConfig {
    return NotificationChannelConfig(
        id = PushNotificationChannels.UPDATES_CHANNEL_ID,
        name = PushNotificationChannels.UPDATES_CHANNEL_NAME,
        description = PushNotificationChannels.UPDATES_CHANNEL_DESCRIPTION,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
    )
}
