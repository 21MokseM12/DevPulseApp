package com.devpulse.app.push

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedPushUpdate(
    val remoteEventId: String?,
    val linkUrl: String,
    val title: String,
    val content: String,
)

@Singleton
class PushPayloadParser
    @Inject
    constructor() {
        fun parse(
            payload: Map<String, String>,
            notificationTitle: String?,
            notificationBody: String?,
            fallbackMessageId: String?,
        ): ParsedPushUpdate? {
            val linkUrl = payload.firstNotBlank("link", "url") ?: return null
            if (!isValidHttpUri(linkUrl)) return null

            val content = payload.firstNotBlank("content", "description", "body") ?: notificationBody?.trim()
            if (content.isNullOrBlank()) return null

            val title =
                payload.firstNotBlank("title")
                    ?: notificationTitle?.trim()
                    ?: DEFAULT_TITLE

            val remoteEventId =
                payload.firstNotBlank("event_id", "eventId", "id")
                    ?: fallbackMessageId?.trim()

            return ParsedPushUpdate(
                remoteEventId = remoteEventId,
                linkUrl = linkUrl.trim(),
                title = title,
                content = content,
            )
        }

        private fun Map<String, String>.firstNotBlank(vararg keys: String): String? {
            return keys.firstNotNullOfOrNull { key ->
                this[key]?.trim()?.takeIf { it.isNotBlank() }
            }
        }

        private fun isValidHttpUri(value: String): Boolean {
            return runCatching { URI(value.trim()) }
                .map { uri ->
                    val scheme = uri.scheme?.lowercase()
                    (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
                }.getOrDefault(false)
        }

        private companion object {
            const val DEFAULT_TITLE = "Новое обновление подписки"
        }
    }
