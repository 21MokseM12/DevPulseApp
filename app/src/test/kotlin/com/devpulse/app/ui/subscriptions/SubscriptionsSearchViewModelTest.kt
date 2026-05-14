package com.devpulse.app.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.domain.usecase.ApplySubscriptionsSearchUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsSearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun search_filtersByTokensAndPresets() {
        runTest {
            val repository =
                FakeSearchRepository(
                    links =
                        listOf(
                            TrackedLink(
                                id = 1L,
                                url = "https://kotlinlang.org/docs",
                                tags = listOf("kotlin", "news"),
                                filters = listOf("contains:kotlin"),
                            ),
                            TrackedLink(
                                id = 2L,
                                url = "https://android.dev/releases",
                                tags = listOf("release"),
                                filters = listOf("contains:android"),
                            ),
                            TrackedLink(
                                id = 3L,
                                url = "https://example.com/plain",
                                tags = emptyList(),
                                filters = emptyList(),
                            ),
                        ),
                )
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.onSearchQueryChanged("tag:kotlin")
            advanceTimeBy(300)
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.links.map { it.id })

            viewModel.onWithFiltersPresetToggled()
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.links.map { it.id })

            viewModel.onSearchQueryChanged("")
            advanceTimeBy(300)
            advanceUntilIdle()
            assertEquals(listOf(2L, 1L), viewModel.uiState.value.links.map { it.id })
        }
    }

    @Test
    fun savedSearchState_restoresAfterRecreateAndRefresh() {
        runTest {
            val repository =
                FakeSearchRepository(
                    links =
                        listOf(
                            TrackedLink(
                                id = 10L,
                                url = "https://alpha.example.dev",
                                tags = listOf("alpha"),
                                filters = emptyList(),
                            ),
                            TrackedLink(
                                id = 11L,
                                url = "https://beta.example.dev",
                                tags = listOf("beta"),
                                filters = listOf("contains:b"),
                            ),
                        ),
                    refreshLinks =
                        listOf(
                            TrackedLink(
                                id = 12L,
                                url = "https://beta.example.dev/new",
                                tags = listOf("beta"),
                                filters = listOf("contains:b"),
                            ),
                        ),
                )
            val savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        "subscriptions_search_query" to "beta",
                        "subscriptions_search_has_filters_only" to true,
                        "subscriptions_search_only_tagged" to true,
                        "subscriptions_search_sort_mode" to SubscriptionsSortMode.RECENTLY_ADDED.name,
                    ),
                )
            val viewModel = createViewModel(repository, savedStateHandle)
            advanceTimeBy(300)
            advanceUntilIdle()
            assertEquals(listOf(11L), viewModel.uiState.value.links.map { it.id })

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf(12L), viewModel.uiState.value.links.map { it.id })
            assertEquals("beta", viewModel.uiState.value.searchState.query)
            assertEquals(2, repository.calls)
        }
    }

    @Test
    fun activeSearch_addMatchingAndNonMatchingLinks_updatesVisibleSubset() {
        runTest {
            val repository =
                FakeSearchRepository(
                    links =
                        listOf(
                            TrackedLink(
                                id = 1L,
                                url = "https://example.dev/kotlin-feed",
                                tags = listOf("kotlin"),
                                filters = listOf("contains:kotlin"),
                            ),
                            TrackedLink(
                                id = 2L,
                                url = "https://example.dev/backend-feed",
                                tags = listOf("backend"),
                                filters = listOf("contains:alerts"),
                            ),
                        ),
                )
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.onTagFilterSelected("kotlin")
            viewModel.onWithFiltersPresetToggled()
            viewModel.onSearchQueryChanged("tag:kotlin")
            advanceSearchDebounce()
            assertEquals(listOf(1L), viewModel.uiState.value.links.map { it.id })

            viewModel.onAddLinkInputChanged("https://example.dev/backend-new")
            viewModel.onAddTagsInputChanged("backend")
            viewModel.onAddFiltersInputChanged("contains:alerts")
            viewModel.addSubscription()
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.links.map { it.id })

            viewModel.onAddLinkInputChanged("https://example.dev/kotlin-new")
            viewModel.onAddTagsInputChanged("kotlin,mobile")
            viewModel.onAddFiltersInputChanged("contains:kotlin")
            viewModel.addSubscription()
            advanceUntilIdle()
            assertEquals(listOf(4L, 1L), viewModel.uiState.value.links.map { it.id })
        }
    }

    @Test
    fun activeSearch_removeHiddenAndVisibleLinks_keepsProjectionConsistent() {
        runTest {
            val repository =
                FakeSearchRepository(
                    links =
                        listOf(
                            TrackedLink(
                                id = 1L,
                                url = "https://example.dev/kotlin-alpha",
                                tags = listOf("kotlin", "mobile"),
                                filters = listOf("contains:kotlin"),
                            ),
                            TrackedLink(
                                id = 2L,
                                url = "https://example.dev/backend",
                                tags = listOf("backend"),
                                filters = listOf("contains:alerts"),
                            ),
                            TrackedLink(
                                id = 3L,
                                url = "https://example.dev/kotlin-beta",
                                tags = listOf("kotlin"),
                                filters = listOf("contains:kotlin"),
                            ),
                        ),
                )
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.onOnlyTaggedPresetToggled()
            viewModel.onTagFilterSelected("kotlin")
            viewModel.onSearchQueryChanged("kotlin")
            advanceSearchDebounce()
            assertEquals(listOf(3L, 1L), viewModel.uiState.value.links.map { it.id })

            val hiddenLink = requireNotNull(viewModel.uiState.value.allLinks.firstOrNull { it.id == 2L })
            viewModel.onRemoveRequested(hiddenLink)
            viewModel.confirmRemove()
            advanceUntilIdle()
            assertEquals(listOf(3L, 1L), viewModel.uiState.value.links.map { it.id })

            val visibleLink = requireNotNull(viewModel.uiState.value.links.firstOrNull { it.id == 3L })
            viewModel.onRemoveRequested(visibleLink)
            viewModel.confirmRemove()
            advanceUntilIdle()
            assertEquals(listOf(1L), viewModel.uiState.value.links.map { it.id })
        }
    }

    private fun createViewModel(
        repository: SubscriptionsRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SubscriptionsViewModel {
        return SubscriptionsViewModel(
            subscriptionsRepository = repository,
            applySubscriptionsSearchUseCase = ApplySubscriptionsSearchUseCase(),
            savedStateHandle = savedStateHandle,
        )
    }

    private fun TestScope.advanceSearchDebounce() {
        advanceTimeBy(300)
        advanceUntilIdle()
    }

    private class FakeSearchRepository(
        private val links: List<TrackedLink>,
        private val refreshLinks: List<TrackedLink> = links,
    ) : SubscriptionsRepository {
        var calls: Int = 0
            private set
        private var storage: MutableList<TrackedLink> = links.toMutableList()
        private var nextId: Long = (links.maxOfOrNull { it.id } ?: 0L) + 1L

        override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult {
            calls += 1
            return if (forceRefresh) {
                storage = refreshLinks.toMutableList()
                SubscriptionsResult.Success(storage.toList())
            } else {
                SubscriptionsResult.Success(storage.toList())
            }
        }

        override suspend fun addSubscription(
            link: String,
            tags: List<String>,
            filters: List<String>,
        ): SubscriptionsResult {
            val added =
                TrackedLink(
                    id = nextId++,
                    url = link,
                    tags = tags,
                    filters = filters,
                )
            storage.add(0, added)
            return SubscriptionsResult.Success(listOf(added))
        }

        override suspend fun removeSubscription(link: String): SubscriptionsResult {
            storage = storage.filterNot { it.url == link }.toMutableList()
            return SubscriptionsResult.Success(emptyList())
        }
    }
}
