package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationPresentationMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationTextResolver
    @Inject
    constructor() {
        fun resolveTitle(rawTitle: String): String {
            val normalized = rawTitle.trim()
            return normalized.ifBlank { DEFAULT_NOTIFICATION_TITLE }
        }

        fun resolveBody(
            rawBody: String,
            presentationMode: NotificationPresentationMode,
        ): String {
            val normalized = rawBody.trim()
            return when (presentationMode) {
                NotificationPresentationMode.Compact -> COMPACT_NOTIFICATION_BODY
                NotificationPresentationMode.Detailed -> normalized.ifBlank { DEFAULT_NOTIFICATION_BODY }
            }
        }

        fun resolveSummaryBody(presentationMode: NotificationPresentationMode): String {
            return when (presentationMode) {
                NotificationPresentationMode.Compact -> COMPACT_SUMMARY_BODY
                NotificationPresentationMode.Detailed -> DETAILED_SUMMARY_BODY
            }
        }

        companion object {
            const val DEFAULT_NOTIFICATION_TITLE = "Новое обновление подписки"
            const val DEFAULT_NOTIFICATION_BODY = "Проверьте новые изменения по отслеживаемой ссылке."
            const val COMPACT_NOTIFICATION_BODY = "Откройте Updates, чтобы увидеть детали."
            const val DETAILED_SUMMARY_BODY = "Новые обновления по подпискам"
            const val COMPACT_SUMMARY_BODY = "Есть новые события в Updates"
        }
    }
