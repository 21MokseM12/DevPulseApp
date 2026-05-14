package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesFilterState
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyUpdatesFiltersUseCaseTest {
    private val useCase = ApplyUpdatesFiltersUseCase()

    @Test
    fun invoke_queryAndUnreadOnly_filtersByIntersection() {
        val events = testEvents()

        val result =
            useCase(
                events = events,
                state =
                    UpdatesFilterState(
                        query = "deploy",
                        unreadOnly = true,
                    ),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun invoke_sourceAndTags_filtersByBothConditions() {
        val events = testEvents()

        val result =
            useCase(
                events = events,
                state =
                    UpdatesFilterState(
                        source = "github",
                        selectedTags = setOf("backend", "release"),
                    ),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun invoke_todayPeriod_keepsOnlyTodayEvents() {
        val events = testEvents()

        val result =
            useCase(
                events = events,
                state = UpdatesFilterState(period = UpdatesPeriodFilter.TODAY),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun invoke_lastSevenDays_excludesOldEvents() {
        val events = testEvents()

        val result =
            useCase(
                events = events,
                state = UpdatesFilterState(period = UpdatesPeriodFilter.LAST_7_DAYS),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun invoke_blankQuery_ignoresWhitespace() {
        val events = testEvents()

        val result =
            useCase(
                events = events,
                state = UpdatesFilterState(query = "   "),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.id })
    }

    private fun testEvents(): List<UpdateEvent> {
        return listOf(
            UpdateEvent(
                id = 1L,
                remoteEventId = "1",
                linkUrl = "https://github.com/org/repo/pull/1",
                title = "Deploy completed",
                content = "Release to production",
                // 2026-05-13T22:00:00Z
                receivedAtEpochMs = 1778712000000L,
                isRead = false,
                source = "github",
                tags = listOf("backend", "release"),
            ),
            UpdateEvent(
                id = 2L,
                remoteEventId = "2",
                linkUrl = "https://jira.example.com/browse/DP-2",
                title = "Incident closed",
                content = "Deployment rollback not needed",
                // 2026-05-08T21:50:00Z
                receivedAtEpochMs = 1778279400000L,
                isRead = true,
                source = "jira",
                tags = listOf("incident"),
            ),
            UpdateEvent(
                id = 3L,
                remoteEventId = "3",
                linkUrl = "https://example.com/ops/3",
                title = "Nightly summary",
                content = "Background health-check",
                // 2026-05-14T01:20:00Z
                receivedAtEpochMs = 1778724000000L,
                isRead = false,
                source = "bot",
                tags = listOf("summary"),
            ),
            UpdateEvent(
                id = 4L,
                remoteEventId = "4",
                linkUrl = "https://example.com/legacy/4",
                title = "Legacy note",
                content = "Old event",
                // 2026-04-05T00:00:00Z
                receivedAtEpochMs = 1775404800000L,
                isRead = false,
                source = "bot",
                tags = listOf("archive"),
            ),
        )
    }

    private companion object {
        const val NOW_EPOCH_MS = 1778774400000L // 2026-05-14T15:00:00Z
    }
}
