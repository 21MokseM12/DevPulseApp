package com.devpulse.app.push

import com.devpulse.app.domain.model.UpdateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigestUpdateAggregatorTest {
    private val aggregator = DigestUpdateAggregator()

    @Test
    fun aggregate_returnsNull_whenNoUpdatesInWindow() {
        val result =
            aggregator.aggregate(
                updates =
                    listOf(
                        update(id = 1L, source = "github", receivedAt = 100L),
                    ),
                periodStartExclusiveEpochMs = 100L,
                periodEndInclusiveEpochMs = 200L,
            )

        assertNull(result)
    }

    @Test
    fun aggregate_groupsUpdatesBySource_andCountsDistinctIds() {
        val result =
            requireNotNull(
                aggregator.aggregate(
                    updates =
                        listOf(
                            update(id = 1L, source = "github", receivedAt = 101L),
                            update(id = 2L, source = "jira", receivedAt = 120L),
                            update(id = 2L, source = "jira", receivedAt = 121L),
                            update(id = 3L, source = "", receivedAt = 150L),
                            update(id = 4L, source = "github", receivedAt = 99L),
                        ),
                    periodStartExclusiveEpochMs = 100L,
                    periodEndInclusiveEpochMs = 150L,
                ),
            )

        assertEquals(3, result.updatesCount)
        assertEquals(mapOf("github" to 1, "jira" to 1, "unknown" to 1), result.sourceBreakdown)
        assertEquals(100L, result.periodStartEpochMs)
        assertEquals(150L, result.periodEndEpochMs)
    }

    private fun update(
        id: Long,
        source: String,
        receivedAt: Long,
    ): UpdateEvent {
        return UpdateEvent(
            id = id,
            remoteEventId = id.toString(),
            linkUrl = "https://example.com/$id",
            title = "title-$id",
            content = "content-$id",
            receivedAtEpochMs = receivedAt,
            isRead = false,
            source = source,
        )
    }
}
