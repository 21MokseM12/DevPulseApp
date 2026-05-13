package com.devpulse.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("https://example.com/article", result.linkUrl)
        assertEquals("Новый пост", result.title)
        assertEquals("Вышла новая статья", result.content)
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
    }

    @Test
    fun parse_missingLink_returnsNull() {
        val result =
            parser.parse(
                payload = mapOf("content" to "payload without url"),
                notificationTitle = "Title",
                notificationBody = null,
                fallbackMessageId = null,
            )

        assertNull(result)
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
    fun parse_invalidLink_returnsNull() {
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

        assertNull(result)
    }
}
