package com.devpulse.app.push

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

        fun resolveBody(rawBody: String): String {
            val normalized = rawBody.trim()
            return normalized.ifBlank { DEFAULT_NOTIFICATION_BODY }
        }

        companion object {
            const val DEFAULT_NOTIFICATION_TITLE = "Новое обновление подписки"
            const val DEFAULT_NOTIFICATION_BODY = "Проверьте новые изменения по отслеживаемой ссылке."
        }
    }
