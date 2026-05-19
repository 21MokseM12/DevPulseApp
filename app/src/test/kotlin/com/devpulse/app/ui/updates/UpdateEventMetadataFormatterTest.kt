package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.UpdateEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateEventMetadataFormatterTest {
    @Test
    fun buildUpdateEventMetadata_githubEvent_containsAuthorAndRepository() {
        val event =
            updateEvent(
                source = "alice",
                linkUrl = "https://github.com/octocat/Hello-World/pull/42",
            )

        val metadata = buildUpdateEventMetadata(event)

        assertEquals(
            listOf("Автор: alice", "Репозиторий: Hello-World"),
            metadata,
        )
    }

    @Test
    fun buildUpdateEventMetadata_stackOverflowEvent_containsAuthorAndQuestionTitle() {
        val event =
            updateEvent(
                source = "bob",
                linkUrl = "https://stackoverflow.com/questions/356897/how-to-center-a-div-in-css",
            )

        val metadata = buildUpdateEventMetadata(event)

        assertEquals(
            listOf("Автор: bob", "Вопрос: how to center a div in css"),
            metadata,
        )
    }

    @Test
    fun buildUpdateEventMetadata_unknownLink_containsOnlyAuthor() {
        val event =
            updateEvent(
                source = "carol",
                linkUrl = "https://example.org/updates/12",
            )

        val metadata = buildUpdateEventMetadata(event)

        assertEquals(listOf("Автор: carol"), metadata)
    }

    private fun updateEvent(
        source: String,
        linkUrl: String,
    ): UpdateEvent {
        return UpdateEvent(
            id = 1L,
            remoteEventId = "1",
            linkUrl = linkUrl,
            title = "Title",
            content = "Content",
            receivedAtEpochMs = 1_000L,
            isRead = false,
            source = source,
            tags = emptyList(),
        )
    }
}
