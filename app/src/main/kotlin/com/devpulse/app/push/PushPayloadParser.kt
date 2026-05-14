package com.devpulse.app.push

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedPushUpdate(
    val remoteEventId: String?,
    val linkUpdateId: Long?,
    val updateOwner: String,
    val creationDate: String,
    val linkUrl: String,
    val title: String,
    val content: String,
    val isCritical: Boolean = false,
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

            val content =
                payload.firstNotBlank("content", "description", "body")
                    ?: notificationBody?.trim()?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_CONTENT

            val title =
                payload.firstNotBlank("title")
                    ?: notificationTitle?.trim()
                    ?: DEFAULT_TITLE

            val remoteEventId =
                payload.firstNotBlank("event_id", "eventId", "id")
                    ?: fallbackMessageId?.trim()
            val linkUpdateId =
                payload.firstNotBlank("id", "update_id", "linkUpdateId", "link_update_id")
                    ?.toLongOrNull()
            val updateOwner =
                payload.firstNotBlank("updateOwner", "update_owner")
                    ?: DEFAULT_UPDATE_OWNER
            val creationDate =
                payload.firstNotBlank("creationDate", "creation_date")
                    ?: DEFAULT_CREATION_DATE
            val isCritical = payload.toCriticalFlag()

            return ParsedPushUpdate(
                remoteEventId = remoteEventId,
                linkUpdateId = linkUpdateId,
                updateOwner = updateOwner,
                creationDate = creationDate,
                linkUrl = linkUrl.trim(),
                title = title,
                content = content,
                isCritical = isCritical,
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

        private fun Map<String, String>.toCriticalFlag(): Boolean {
            val criticalValue = firstNotBlank("critical", "isCritical", "critical_event")
            if (criticalValue != null) {
                return parseBooleanLike(criticalValue)
            }
            val priorityValue = firstNotBlank("priority", "severity", "level")
            if (priorityValue != null) {
                return priorityValue.lowercase() in PRIORITY_CRITICAL_VALUES
            }
            return false
        }

        private fun parseBooleanLike(rawValue: String): Boolean {
            return rawValue.lowercase() in BOOLEAN_TRUE_VALUES
        }

        private companion object {
            const val DEFAULT_TITLE = "Новое обновление подписки"
            const val DEFAULT_CONTENT = "Проверьте новые изменения по отслеживаемой ссылке."
            const val DEFAULT_UPDATE_OWNER = "unknown"
            const val DEFAULT_CREATION_DATE = ""
            val BOOLEAN_TRUE_VALUES = setOf("true", "1", "yes", "y", "on")
            val PRIORITY_CRITICAL_VALUES = setOf("critical", "high", "urgent")
        }
    }
