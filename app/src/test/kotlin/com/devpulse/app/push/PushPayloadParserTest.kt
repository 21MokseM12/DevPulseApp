package com.devpulse.app.push

import org.junit.Assert.assertEquals
import org.junit.Test

class PushPayloadParserTest {
    private val parser = PushPayloadParser()

    @Test
    fun parse_validPayload_returnsParsedUpdate() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "event_id" to "evt-10",
                        "id" to "10",
                        "update_owner" to "scrapper",
                        "creation_date" to "2026-05-13T20:00:00Z",
                        "url" to "https://example.com/article",
                        "title" to "Новый пост",
                        "content" to "Вышла новая статья",
                    ),
                notificationTitle = null,
                notificationBody = null,
                fallbackMessageId = "fallback-id",
            )

        requireNotNull(result)
        assertEquals("evt-10", result.remoteEventId)
        assertEquals(10L, result.linkUpdateId)
        assertEquals("scrapper", result.updateOwner)
        assertEquals("2026-05-13T20:00:00Z", result.creationDate)
        assertEquals("https://example.com/article", result.linkUrl)
        assertEquals("Новый пост", result.title)
        assertEquals("Вышла новая статья", result.content)
        assertEquals(false, result.isCritical)
    }

    @Test
    fun parse_withoutDataTitle_usesNotificationTitle() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "link" to "https://example.com/article",
                        "body" to "Только body в data",
                    ),
                notificationTitle = "Fallback title",
                notificationBody = null,
                fallbackMessageId = "msg-7",
            )

        requireNotNull(result)
        assertEquals("Fallback title", result.title)
        assertEquals("msg-7", result.remoteEventId)
        assertEquals("unknown", result.updateOwner)
        assertEquals("", result.creationDate)
    }

    @Test
    fun parse_withLinkUpdateAliasFields_mapsContractFields() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "eventId" to "evt-77",
                        "link_update_id" to "77",
                        "updateOwner" to "bot",
                        "creationDate" to "2026-05-14T10:00:00Z",
                        "link" to "https://example.com/feed",
                        "description" to "Contract payload",
                    ),
                notificationTitle = null,
                notificationBody = null,
                fallbackMessageId = null,
            )

        requireNotNull(result)
        assertEquals("evt-77", result.remoteEventId)
        assertEquals(77L, result.linkUpdateId)
        assertEquals("bot", result.updateOwner)
        assertEquals("2026-05-14T10:00:00Z", result.creationDate)
    }

    @Test
    fun parse_withBackendPushContractAliases_mapsCreatedAtAndSource() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "event_id" to "evt-contract",
                        "url" to "https://example.com/contract",
                        "title" to "Contract title",
                        "content" to "Contract content",
                        "created_at" to "2026-05-15T11:00:00Z",
                        "source" to "scrapper",
                    ),
                notificationTitle = null,
                notificationBody = null,
                fallbackMessageId = null,
            )

        requireNotNull(result)
        assertEquals("scrapper", result.updateOwner)
        assertEquals("2026-05-15T11:00:00Z", result.creationDate)
    }

    @Test
    fun parse_missingLink_usesEmptyLinkFallback() {
        val result =
            parser.parse(
                payload = mapOf("content" to "payload without url"),
                notificationTitle = "Title",
                notificationBody = null,
                fallbackMessageId = null,
            )

        requireNotNull(result)
        assertEquals("", result.linkUrl)
        assertEquals("payload without url", result.content)
    }

    @Test
    fun parse_missingContent_returnsNull() {
        val result =
            parser.parse(
                payload = mapOf("url" to "https://example.com/article"),
                notificationTitle = "Title",
                notificationBody = " ",
                fallbackMessageId = null,
            )

        requireNotNull(result)
        assertEquals("Проверьте новые изменения по отслеживаемой ссылке.", result.content)
    }

    @Test
    fun parse_invalidLink_keepsNotificationWithEmptyLink() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "url" to "not-a-url",
                        "content" to "Body",
                    ),
                notificationTitle = "Title",
                notificationBody = "Notification body",
                fallbackMessageId = "msg-11",
            )

        requireNotNull(result)
        assertEquals("", result.linkUrl)
        assertEquals("Body", result.content)
    }

    @Test
    fun parse_notificationOnlyWithoutDataLink_buildsFallbackUpdate() {
        val result =
            parser.parse(
                payload = emptyMap(),
                notificationTitle = "Событие",
                notificationBody = "Обновление по подписке",
                fallbackMessageId = "msg-notification-only",
            )

        requireNotNull(result)
        assertEquals("", result.linkUrl)
        assertEquals("Событие", result.title)
        assertEquals("Обновление по подписке", result.content)
        assertEquals("msg-notification-only", result.remoteEventId)
    }

    @Test
    fun parse_withCriticalFlag_setsCriticalUpdate() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "url" to "https://example.com/article",
                        "content" to "Body",
                        "critical" to "true",
                    ),
                notificationTitle = "Title",
                notificationBody = null,
                fallbackMessageId = "msg-critical",
            )

        requireNotNull(result)
        assertEquals(true, result.isCritical)
    }

    @Test
    fun parse_withPriorityFlag_setsCriticalUpdate() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "url" to "https://example.com/article",
                        "content" to "Body",
                        "priority" to "critical",
                    ),
                notificationTitle = "Title",
                notificationBody = null,
                fallbackMessageId = "msg-priority",
            )

        requireNotNull(result)
        assertEquals(true, result.isCritical)
    }

    @Test
    fun parse_withUpdateUrl_prefersChangeLinkOverSubscriptionLink() {
        val result =
            parser.parse(
                payload =
                    mapOf(
                        "link" to "https://github.com/org/repo",
                        "update_url" to "https://github.com/org/repo/commit/abc123",
                        "content" to "Body",
                    ),
                notificationTitle = "Title",
                notificationBody = null,
                fallbackMessageId = "msg-update-url",
            )

        requireNotNull(result)
        assertEquals("https://github.com/org/repo/commit/abc123", result.linkUrl)
    }
}
