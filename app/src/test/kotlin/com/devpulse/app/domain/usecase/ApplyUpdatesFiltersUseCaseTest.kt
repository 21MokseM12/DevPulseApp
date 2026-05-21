package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.TagMatchMode
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesFilterState
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyUpdatesFiltersUseCaseTest {
    private val useCase = ApplyUpdatesFiltersUseCase()

    @Test
    fun invoke_filterCombinationsAndConflicts_returnsExpectedIds() {
        val events = testEvents()
        val cases =
            listOf(
                FilterCase(
                    name = "no filters",
                    state = UpdatesFilterState(),
                    expectedIds = listOf(1L, 2L, 3L, 4L),
                ),
                FilterCase(
                    name = "query only",
                    state = UpdatesFilterState(query = "deploy"),
                    expectedIds = listOf(1L, 2L),
                ),
                FilterCase(
                    name = "unread only",
                    state = UpdatesFilterState(unreadOnly = true),
                    expectedIds = listOf(1L, 3L, 4L),
                ),
                FilterCase(
                    name = "source only",
                    state = UpdatesFilterState(source = "bot"),
                    expectedIds = listOf(3L, 4L),
                ),
                FilterCase(
                    name = "period today",
                    state = UpdatesFilterState(period = UpdatesPeriodFilter.TODAY),
                    expectedIds = listOf(3L),
                ),
                FilterCase(
                    name = "period last 7 days",
                    state = UpdatesFilterState(period = UpdatesPeriodFilter.LAST_7_DAYS),
                    expectedIds = listOf(1L, 2L, 3L),
                ),
                FilterCase(
                    name = "single tag",
                    state =
                        UpdatesFilterState(
                            selectedTags = setOf("backend"),
                            tagMatchMode = TagMatchMode.ANY,
                        ),
                    expectedIds = listOf(1L),
                ),
                FilterCase(
                    name = "multiple tags intersection",
                    state =
                        UpdatesFilterState(
                            selectedTags = setOf("backend", "release"),
                            tagMatchMode = TagMatchMode.ALL,
                        ),
                    expectedIds = listOf(1L),
                ),
                FilterCase(
                    name = "query + unread",
                    state =
                        UpdatesFilterState(
                            query = "deploy",
                            unreadOnly = true,
                        ),
                    expectedIds = listOf(1L),
                ),
                FilterCase(
                    name = "source + tags",
                    state =
                        UpdatesFilterState(
                            source = "github",
                            selectedTags = setOf("backend", "release"),
                            tagMatchMode = TagMatchMode.ALL,
                        ),
                    expectedIds = listOf(1L),
                ),
                FilterCase(
                    name = "link filter only",
                    state = UpdatesFilterState(selectedLinkFilters = setOf("contains:kotlin")),
                    expectedIds = listOf(1L, 3L),
                ),
                FilterCase(
                    name = "link filter + source conflict",
                    state =
                        UpdatesFilterState(
                            source = "jira",
                            selectedLinkFilters = setOf("contains:kotlin"),
                        ),
                    expectedIds = emptyList(),
                ),
                FilterCase(
                    name = "source + period",
                    state =
                        UpdatesFilterState(
                            source = "bot",
                            period = UpdatesPeriodFilter.LAST_30_DAYS,
                        ),
                    expectedIds = listOf(3L),
                ),
                FilterCase(
                    name = "source + period conflict",
                    state =
                        UpdatesFilterState(
                            source = "jira",
                            period = UpdatesPeriodFilter.TODAY,
                        ),
                    expectedIds = emptyList(),
                ),
                FilterCase(
                    name = "tags conflict",
                    state =
                        UpdatesFilterState(
                            selectedTags = setOf("backend", "summary"),
                            tagMatchMode = TagMatchMode.ALL,
                        ),
                    expectedIds = emptyList(),
                ),
                FilterCase(
                    name = "query + source conflict",
                    state =
                        UpdatesFilterState(
                            query = "deploy",
                            source = "bot",
                        ),
                    expectedIds = emptyList(),
                ),
                FilterCase(
                    name = "all filters success",
                    state =
                        UpdatesFilterState(
                            query = "nightly",
                            unreadOnly = true,
                            source = "bot",
                            period = UpdatesPeriodFilter.TODAY,
                            selectedTags = setOf("summary"),
                            tagMatchMode = TagMatchMode.ANY,
                        ),
                    expectedIds = listOf(3L),
                ),
                FilterCase(
                    name = "all filters conflict",
                    state =
                        UpdatesFilterState(
                            query = "deploy",
                            unreadOnly = true,
                            source = "jira",
                            period = UpdatesPeriodFilter.TODAY,
                            selectedTags = setOf("incident"),
                            tagMatchMode = TagMatchMode.ANY,
                        ),
                    expectedIds = emptyList(),
                ),
                FilterCase(
                    name = "explicit digest window",
                    state =
                        UpdatesFilterState(
                            periodStartEpochMs = 1778712000001L,
                            periodEndEpochMs = 1778724000000L,
                        ),
                    expectedIds = listOf(3L),
                ),
            )

        cases.forEach { case ->
            val result =
                useCase(
                    events = events,
                    state = case.state,
                    nowEpochMs = NOW_EPOCH_MS,
                )

            assertEquals(case.name, case.expectedIds, result.map { it.id })
        }
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

    @Test
    fun invoke_multipleTags_respectsAnyAndAllMatchModes() {
        val events = testEvents()

        val anyResult =
            useCase(
                events = events,
                state =
                    UpdatesFilterState(
                        selectedTags = setOf("backend", "incident"),
                        tagMatchMode = TagMatchMode.ANY,
                    ),
                nowEpochMs = NOW_EPOCH_MS,
            )
        val allResult =
            useCase(
                events = events,
                state =
                    UpdatesFilterState(
                        selectedTags = setOf("backend", "incident"),
                        tagMatchMode = TagMatchMode.ALL,
                    ),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L, 2L), anyResult.map { it.id })
        assertEquals(emptyList<Long>(), allResult.map { it.id })
    }

    @Test
    fun invoke_tagsAreMixedCaseOrDirty_normalizesBeforeMatching() {
        val events = testEvents() + dirtyTagsEvent()

        val result =
            useCase(
                events = events,
                state =
                    UpdatesFilterState(
                        selectedTags = setOf(" BACKEND ", " "),
                        tagMatchMode = TagMatchMode.ANY,
                    ),
                nowEpochMs = NOW_EPOCH_MS,
            )

        assertEquals(listOf(1L, 5L), result.map { it.id })
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
                linkFilters = listOf("contains:kotlin", "author:team"),
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
                linkFilters = listOf("contains:incident"),
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
                linkFilters = listOf("contains:kotlin"),
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

    private fun dirtyTagsEvent(): UpdateEvent {
        return UpdateEvent(
            id = 5L,
            remoteEventId = "5",
            linkUrl = "https://example.com/dirty/5",
            title = "Dirty tags",
            content = "Has noisy tags",
            receivedAtEpochMs = 1778712000000L,
            isRead = false,
            source = "github",
            tags = listOf("  BACKEND  ", "", "   "),
        )
    }

    private companion object {
        const val NOW_EPOCH_MS = 1778774400000L // 2026-05-14T15:00:00Z
    }

    private data class FilterCase(
        val name: String,
        val state: UpdatesFilterState,
        val expectedIds: List<Long>,
    )
}
