package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.SubscriptionsSearchState
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplySubscriptionsSearchUseCaseTest {
    private val useCase = ApplySubscriptionsSearchUseCase()

    @Test
    fun invoke_filtersByUrlTagsFiltersAndTokens() {
        val links = testLinks()

        val byUrl =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "android.dev"),
            )
        assertEquals(listOf(2L), byUrl.map { it.id })

        val byTagToken =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "tag:news"),
            )
        assertEquals(listOf(3L, 1L), byTagToken.map { it.id })

        val byFilterToken =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "filter:kotlin"),
            )
        assertEquals(listOf(2L, 1L), byFilterToken.map { it.id })

        val byTagFilter =
            useCase(
                links = links,
                state = SubscriptionsSearchState(tagFilter = "release"),
            )
        assertEquals(listOf(2L), byTagFilter.map { it.id })
    }

    @Test
    fun invoke_appliesQuickPresets() {
        val links = testLinks()

        val onlyTagged =
            useCase(
                links = links,
                state = SubscriptionsSearchState(onlyTagged = true),
            )
        assertEquals(listOf(3L, 2L, 1L), onlyTagged.map { it.id })

        val withFiltersOnly =
            useCase(
                links = links,
                state = SubscriptionsSearchState(hasFiltersOnly = true),
            )
        assertEquals(listOf(2L, 1L), withFiltersOnly.map { it.id })
    }

    @Test
    fun invoke_sortsByUrlWhenSelected() {
        val result =
            useCase(
                links = testLinks(),
                state = SubscriptionsSearchState(sortMode = SubscriptionsSortMode.URL_ASCENDING),
            )

        assertEquals(
            listOf(
                "https://android.dev/releases",
                "https://kotlinlang.org/docs/flow.html",
                "https://news.ycombinator.com",
                "https://zeta.example.com/feed",
            ),
            result.map { it.url },
        )
    }

    @Test
    fun invoke_blankQueryMatchesAll() {
        val result =
            useCase(
                links = testLinks(),
                state = SubscriptionsSearchState(query = "   "),
            )

        assertEquals(listOf(4L, 3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun invoke_largeDataset_keepsBehaviorForIndexedTerms() {
        val targetTagged =
            TrackedLink(
                id = 9_001L,
                url = "https://feeds.example.dev/kotlin/weekly",
                tags = listOf("kotlin-digest", "mobile"),
                filters = listOf("contains:kotlin"),
            )
        val targetWithFilter =
            TrackedLink(
                id = 9_002L,
                url = "https://feeds.example.dev/android/release",
                tags = listOf("android"),
                filters = listOf("contains:android-release"),
            )
        val links =
            buildList {
                repeat(500) { index ->
                    add(
                        TrackedLink(
                            id = index.toLong(),
                            url = "https://noise.example.dev/item-$index",
                            tags = listOf("tag-$index"),
                            filters = listOf("contains:$index"),
                        ),
                    )
                }
                add(targetTagged)
                add(targetWithFilter)
            }

        val byTagToken =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "tag:kotlin"),
            )
        assertEquals(listOf(9_001L), byTagToken.map { it.id })

        val byFilterToken =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "filter:android-release"),
            )
        assertEquals(listOf(9_002L), byFilterToken.map { it.id })

        val byMixedQuery =
            useCase(
                links = links,
                state = SubscriptionsSearchState(query = "url:feeds.example.dev mobile"),
            )
        assertEquals(listOf(9_001L), byMixedQuery.map { it.id })
    }

    private fun testLinks(): List<TrackedLink> {
        return listOf(
            TrackedLink(
                id = 1L,
                url = "https://kotlinlang.org/docs/flow.html",
                tags = listOf("kotlin", "news"),
                filters = listOf("contains:kotlin"),
            ),
            TrackedLink(
                id = 2L,
                url = "https://android.dev/releases",
                tags = listOf("release"),
                filters = listOf("contains:kotlin", "contains:android"),
            ),
            TrackedLink(
                id = 3L,
                url = "https://news.ycombinator.com",
                tags = listOf("news"),
                filters = emptyList(),
            ),
            TrackedLink(
                id = 4L,
                url = "https://zeta.example.com/feed",
                tags = emptyList(),
                filters = emptyList(),
            ),
        )
    }
}
