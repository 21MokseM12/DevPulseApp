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

        assertNull(result)
    }
}
