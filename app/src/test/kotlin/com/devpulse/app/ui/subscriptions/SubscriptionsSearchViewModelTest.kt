package com.devpulse.app.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.domain.usecase.ApplySubscriptionsSearchUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private class FakeSearchRepository(
        private val links: List<TrackedLink>,
        private val refreshLinks: List<TrackedLink> = links,
    ) : SubscriptionsRepository {
        var calls: Int = 0
            private set

        override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult {
            calls += 1
            return if (forceRefresh) {
                SubscriptionsResult.Success(refreshLinks)
            } else {
                SubscriptionsResult.Success(links)
            }
        }

        override suspend fun addSubscription(
            link: String,
            tags: List<String>,
            filters: List<String>,
        ): SubscriptionsResult {
            return SubscriptionsResult.Success(emptyList())
        }

        override suspend fun removeSubscription(link: String): SubscriptionsResult {
            return SubscriptionsResult.Success(emptyList())
        }
    }
}
