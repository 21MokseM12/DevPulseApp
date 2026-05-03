package com.devpulse.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.devpulse.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val textResolver: PushNotificationTextResolver,
    ) {
        private val manager by lazy { NotificationManagerCompat.from(context) }

        @SuppressLint("MissingPermission")
        fun showUpdateNotification(update: ParsedPushUpdate) {
            if (!canPostNotifications()) return

            val notificationId = notificationId(update)
            val contentIntent = buildOpenUpdatesPendingIntent()
            val notification =
                NotificationCompat
                    .Builder(context, PushNotificationChannels.UPDATES_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(textResolver.resolveTitle(update.title))
                    .setContentText(textResolver.resolveBody(update.content))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            textResolver.resolveBody(update.content),
                        ),
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(UPDATES_GROUP_KEY)
                    .setContentIntent(contentIntent)
                    .build()

            runCatching {
                manager.notify(notificationId, notification)
                manager.notify(SUMMARY_NOTIFICATION_ID, buildSummaryNotification(contentIntent))
            }.onFailure { error ->
                Log.w(LOG_TAG, "Не удалось показать системное уведомление", error)
            }
        }

        private fun buildSummaryNotification(contentIntent: PendingIntent): android.app.Notification {
            return NotificationCompat
                .Builder(context, PushNotificationChannels.UPDATES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("DevPulse")
                .setContentText("Новые обновления по подпискам")
                .setAutoCancel(true)
                .setGroup(UPDATES_GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(contentIntent)
                .build()
        }

        private fun buildOpenUpdatesPendingIntent(): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    putExtra(PushNotificationNavigation.EXTRA_OPEN_UPDATES, true)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            return PendingIntent.getActivity(
                context,
                UPDATES_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun notificationId(update: ParsedPushUpdate): Int {
            val seed = update.remoteEventId ?: "${update.linkUrl}:${update.content}"
            return seed.hashCode() and Int.MAX_VALUE
        }

        private fun canPostNotifications(): Boolean {
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

        private companion object {
            const val LOG_TAG = "PushNotificationManager"
            const val UPDATES_GROUP_KEY = "devpulse_updates_group"
            const val SUMMARY_NOTIFICATION_ID = 1001
            const val UPDATES_INTENT_REQUEST_CODE = 2001
        }
    }
