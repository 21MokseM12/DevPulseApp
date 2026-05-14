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
import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val textResolver: PushNotificationTextResolver,
    ) : PushNotifier {
        private val manager by lazy { NotificationManagerCompat.from(context) }

        @SuppressLint("MissingPermission")
        override fun showUpdateNotification(
            update: ParsedPushUpdate,
            presentationMode: NotificationPresentationMode,
            digestMode: NotificationDigestMode?,
        ) {
            if (!canPostNotifications()) return

            val notificationId = notificationIdForUpdate(update)
            val contentIntent = buildOpenUpdatesPendingIntent(filterUnreadOnly = false)
            val title = textResolver.resolveTitle(update.title)
            val body = textResolver.resolveBody(update.content, presentationMode)
            val notification =
                NotificationCompat
                    .Builder(context, PushNotificationChannels.UPDATES_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(title)
                    .setContentText(body)
                    .let { builder ->
                        if (presentationMode == NotificationPresentationMode.Detailed) {
                            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        } else {
                            builder
                        }
                    }
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(UPDATES_GROUP_KEY)
                    .setContentIntent(contentIntent)
                    .build()

            runCatching {
                if (shouldPostIndividualNotification(digestMode)) {
                    manager.notify(notificationId, notification)
                }
                if (digestMode == null) {
                    manager.notify(
                        SUMMARY_NOTIFICATION_ID,
                        buildSummaryNotification(contentIntent, presentationMode, digestMode),
                    )
                }
            }.onFailure { error ->
                Log.w(LOG_TAG, "Не удалось показать системное уведомление", error)
            }
        }

        @SuppressLint("MissingPermission")
        override fun showDigestNotification(
            summary: DigestSummaryPayload,
            digestMode: NotificationDigestMode,
        ) {
            if (!canPostNotifications()) return

            val contentIntent =
                buildOpenUpdatesPendingIntent(
                    filterUnreadOnly = true,
                    digestPeriodStartEpochMs = summary.periodStartEpochMs,
                    digestPeriodEndEpochMs = summary.periodEndEpochMs,
                )
            val title = textResolver.resolveDigestSummaryBody(digestMode)
            val body = textResolver.resolveDigestBody(summary)
            val notification =
                NotificationCompat
                    .Builder(context, PushNotificationChannels.UPDATES_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setGroup(UPDATES_GROUP_KEY)
                    .setContentIntent(contentIntent)
                    .build()
            runCatching {
                manager.notify(DIGEST_NOTIFICATION_ID, notification)
            }.onFailure { error ->
                Log.w(LOG_TAG, "Не удалось показать digest-уведомление", error)
            }
        }

        private fun buildSummaryNotification(
            contentIntent: PendingIntent,
            presentationMode: NotificationPresentationMode,
            digestMode: NotificationDigestMode?,
        ): android.app.Notification {
            val summaryBody =
                if (digestMode == null) {
                    textResolver.resolveSummaryBody(presentationMode)
                } else {
                    textResolver.resolveDigestSummaryBody(digestMode)
                }
            return NotificationCompat
                .Builder(context, PushNotificationChannels.UPDATES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("DevPulse")
                .setContentText(summaryBody)
                .setAutoCancel(true)
                .setGroup(UPDATES_GROUP_KEY)
                .setGroupSummary(true)
                .setContentIntent(contentIntent)
                .build()
        }

        private fun buildOpenUpdatesPendingIntent(filterUnreadOnly: Boolean): PendingIntent {
            val intent =
                buildOpenUpdatesIntent(
                    context = context,
                    filterUnreadOnly = filterUnreadOnly,
                )
            return PendingIntent.getActivity(
                context,
                if (filterUnreadOnly) DIGEST_UPDATES_INTENT_REQUEST_CODE else UPDATES_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun buildOpenUpdatesPendingIntent(
            filterUnreadOnly: Boolean,
            digestPeriodStartEpochMs: Long?,
            digestPeriodEndEpochMs: Long?,
        ): PendingIntent {
            val intent =
                buildOpenUpdatesIntent(
                    context = context,
                    filterUnreadOnly = filterUnreadOnly,
                    digestPeriodStartEpochMs = digestPeriodStartEpochMs,
                    digestPeriodEndEpochMs = digestPeriodEndEpochMs,
                )
            return PendingIntent.getActivity(
                context,
                if (filterUnreadOnly) DIGEST_UPDATES_INTENT_REQUEST_CODE else UPDATES_INTENT_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun notificationId(update: ParsedPushUpdate): Int {
            return notificationIdForUpdate(update)
        }

        private fun canPostNotifications(): Boolean {
            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            return canPostNotifications(
                areNotificationsEnabled = manager.areNotificationsEnabled(),
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = permissionGranted,
            )
        }

        private companion object {
            const val LOG_TAG = "PushNotificationManager"
            const val UPDATES_GROUP_KEY = "devpulse_updates_group"
            const val SUMMARY_NOTIFICATION_ID = 1001
            const val DIGEST_NOTIFICATION_ID = 1002
            const val UPDATES_INTENT_REQUEST_CODE = 2001
            const val DIGEST_UPDATES_INTENT_REQUEST_CODE = 2002
        }
    }

internal fun notificationIdForUpdate(update: ParsedPushUpdate): Int {
    val seed = update.remoteEventId ?: "${update.linkUrl}:${update.content}"
    return seed.hashCode() and Int.MAX_VALUE
}

internal fun canPostNotifications(
    areNotificationsEnabled: Boolean,
    sdkInt: Int,
    permissionGranted: Boolean,
): Boolean {
    if (!areNotificationsEnabled) return false
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) return true
    return permissionGranted
}

internal fun shouldPostIndividualNotification(digestMode: NotificationDigestMode?): Boolean {
    return DigestDeliveryContract.shouldDeliverInstantNotification(digestMode)
}

internal fun buildOpenUpdatesIntent(
    context: Context,
    filterUnreadOnly: Boolean,
    digestPeriodStartEpochMs: Long? = null,
    digestPeriodEndEpochMs: Long? = null,
): Intent {
    return Intent(context, MainActivity::class.java).applyOpenUpdatesExtras(
        filterUnreadOnly = filterUnreadOnly,
        digestPeriodStartEpochMs = digestPeriodStartEpochMs,
        digestPeriodEndEpochMs = digestPeriodEndEpochMs,
    )
}

internal fun Intent.applyOpenUpdatesExtras(
    filterUnreadOnly: Boolean,
    digestPeriodStartEpochMs: Long? = null,
    digestPeriodEndEpochMs: Long? = null,
): Intent {
    putExtra(PushNotificationNavigation.EXTRA_OPEN_UPDATES, true)
    putExtra(PushNotificationNavigation.EXTRA_FILTER_UNREAD_ONLY, filterUnreadOnly)
    val digestWindow =
        digestWindowOrNull(
            periodStartEpochMs = digestPeriodStartEpochMs,
            periodEndEpochMs = digestPeriodEndEpochMs,
        )
    if (digestWindow != null) {
        putExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_START_EPOCH_MS, digestWindow.first)
        putExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_END_EPOCH_MS, digestWindow.second)
    }
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    return this
}

internal fun digestWindowOrNull(
    periodStartEpochMs: Long?,
    periodEndEpochMs: Long?,
): Pair<Long, Long>? {
    if (periodStartEpochMs == null || periodEndEpochMs == null) return null
    return periodStartEpochMs to periodEndEpochMs
}
