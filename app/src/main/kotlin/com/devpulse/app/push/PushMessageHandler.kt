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
    val suppressionReason: NotificationSuppressionReason? = null,
    val appInForeground: Boolean = false,
    val permissionGranted: Boolean = false,
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
    val digestMode: NotificationDigestMode? = null,
)

enum class NotificationSuppressionReason {
    Foreground,
    DigestMode,
    QuietHours,
    PermissionDenied,
    DisabledInPreferences,
}

@Singleton
class PushMessageHandler
    @Inject
    constructor(
        private val payloadParser: PushPayloadParser,
        private val updatesRepository: UpdatesRepository,
        private val notificationPreferencesStore: NotificationPreferencesStore,
        private val quietHoursPolicyEvaluator: QuietHoursPolicyEvaluator,
        private val appVisibilityTracker: AppVisibilityProvider,
        private val notificationCapabilityChecker: NotificationCapabilityProvider,
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
                val appInForeground = appVisibilityTracker.isAppInForeground()
                val permissionGranted = notificationCapabilityChecker.canPostNotifications()
                val suppressionReason =
                    resolveSuppressionReason(
                        preferences = preferences,
                        appInForeground = appInForeground,
                        permissionGranted = permissionGranted,
                        suppressedByQuietHours = suppressedByQuietHours,
                    )
                PushHandleOutcome(
                    result = PushHandleResult.Saved,
                    update = parsed,
                    shouldShowSystemNotification = suppressionReason == null,
                    suppressedByQuietHours = suppressedByQuietHours,
                    suppressionReason = suppressionReason,
                    appInForeground = appInForeground,
                    permissionGranted = permissionGranted,
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

        private fun resolveSuppressionReason(
            preferences: NotificationPreferences,
            appInForeground: Boolean,
            permissionGranted: Boolean,
            suppressedByQuietHours: Boolean,
        ): NotificationSuppressionReason? {
            if (!preferences.enabled) return NotificationSuppressionReason.DisabledInPreferences
            if (appInForeground) return NotificationSuppressionReason.Foreground
            if (!permissionGranted) return NotificationSuppressionReason.PermissionDenied
            if (suppressedByQuietHours) return NotificationSuppressionReason.QuietHours
            if (preferences.digestMode != null) return NotificationSuppressionReason.DigestMode
            return null
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
