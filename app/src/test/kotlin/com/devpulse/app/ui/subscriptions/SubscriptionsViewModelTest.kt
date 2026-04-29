package com.devpulse.app.ui.subscriptions

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsContentState() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Success(
                                    links =
                                        listOf(
                                            TrackedLink(
                                                id = 1L,
                                                url = "https://example.org",
                                                tags = listOf("news"),
                                                filters = listOf("contains:kotlin"),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertFalse(state.isRefreshing)
            assertEquals(1, state.links.size)
            assertEquals(null, state.errorMessage)
        }
    }

    @Test
    fun init_handlesEmptyList() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(links = emptyList()))),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.links.isEmpty())
            assertEquals(null, state.errorMessage)
        }
    }

    @Test
    fun init_handlesErrorState() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Failure(
                                    error =
                                        ApiError(
                                            kind = ApiErrorKind.Network,
                                            userMessage = "Ошибка сети",
                                        ),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.links.isEmpty())
            assertEquals("Ошибка сети", state.errorMessage)
        }
    }

    @Test
    fun refresh_requestsDataWithoutRecreatingViewModel() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Success(
                                    links =
                                        listOf(
                                            TrackedLink(
                                                id = 1L,
                                                url = "https://example.org",
                                                tags = emptyList(),
                                                filters = emptyList(),
                                            ),
                                        ),
                                ),
                                SubscriptionsResult.Success(
                                    links =
                                        listOf(
                                            TrackedLink(
                                                id = 2L,
                                                url = "https://example.com",
                                                tags = listOf("dev"),
                                                filters = emptyList(),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(2L, viewModel.uiState.value.links.first().id)
            assertEquals(2, repository.calls)
        }
    }

    @Test
    fun refresh_ignoresDuplicateRequestsWhileRefreshing() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(links = emptyList()))),
                    gate = gate,
                )
            val viewModel = SubscriptionsViewModel(repository)
            runCurrent()

            viewModel.refresh()
            viewModel.refresh()
            runCurrent()

            assertEquals(1, repository.calls)
            gate.complete(Unit)
        }
    }

    @Test
    fun retry_afterError_loadsContent() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Failure(
                                    error =
                                        ApiError(
                                            kind = ApiErrorKind.Network,
                                            userMessage = "Ошибка сети",
                                        ),
                                ),
                                SubscriptionsResult.Success(
                                    links =
                                        listOf(
                                            TrackedLink(
                                                id = 5L,
                                                url = "https://example.dev",
                                                tags = listOf("dev"),
                                                filters = emptyList(),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            assertEquals("Ошибка сети", viewModel.uiState.value.errorMessage)
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.errorMessage)
            assertEquals(1, viewModel.uiState.value.links.size)
            assertEquals(5L, viewModel.uiState.value.links.first().id)
        }
    }

    private class FakeSubscriptionsRepository(
        private val results: ArrayDeque<SubscriptionsResult>,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : SubscriptionsRepository {
        var calls: Int = 0
            private set

        override suspend fun getSubscriptions(): SubscriptionsResult {
            calls += 1
            gate?.await()
            return results.removeFirstOrNull() ?: SubscriptionsResult.Success(links = emptyList())
        }
    }
}
