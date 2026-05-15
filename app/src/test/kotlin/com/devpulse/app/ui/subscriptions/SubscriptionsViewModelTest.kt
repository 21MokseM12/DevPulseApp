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
            assertEquals(SubscriptionsScreenState.Content, state.screenState)
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
            assertEquals(SubscriptionsScreenState.Empty, state.screenState)
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
            assertEquals(SubscriptionsScreenState.Error, state.screenState)
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
            assertEquals(SubscriptionsScreenState.Content, viewModel.uiState.value.screenState)
        }
    }

    @Test
    fun init_withCachedStaleData_triggersBackgroundRefreshAndClearsStaleFlag() {
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
                                                url = "https://cached.example",
                                                tags = emptyList(),
                                                filters = emptyList(),
                                            ),
                                        ),
                                    isStale = true,
                                    lastSyncAtEpochMs = 100L,
                                ),
                                SubscriptionsResult.Success(
                                    links =
                                        listOf(
                                            TrackedLink(
                                                id = 2L,
                                                url = "https://fresh.example",
                                                tags = emptyList(),
                                                filters = emptyList(),
                                            ),
                                        ),
                                    isStale = false,
                                    lastSyncAtEpochMs = 200L,
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2L, state.links.first().id)
            assertFalse(state.isStaleData)
            assertEquals(2, repository.calls)
            assertEquals(1, repository.forceRefreshCalls)
        }
    }

    @Test
    fun addSubscription_withInvalidUrl_showsValidationError() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("not-url")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals("Введите корректный URL (http/https).", viewModel.uiState.value.addErrorMessage)
            assertEquals(0, repository.addCalls)
        }
    }

    @Test
    fun addSubscription_successfullyAddsItemWithoutManualReload() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Success(
                            listOf(
                                TrackedLink(
                                    id = 42L,
                                    url = "https://example.com/new",
                                    tags = listOf("dev"),
                                    filters = listOf("contains:kotlin"),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://example.com/new")
            viewModel.onAddTagsInputChanged("dev")
            viewModel.onAddFiltersInputChanged("contains:kotlin")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(1, repository.addCalls)
            assertEquals(42L, viewModel.uiState.value.links.first().id)
            assertEquals("", viewModel.uiState.value.addLinkInput)
        }
    }

    @Test
    fun addSubscription_backendErrorShownNearForm() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Подписка уже существует",
                                ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://example.com/existing")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals("Подписка уже существует", viewModel.uiState.value.addErrorMessage)
            assertTrue(viewModel.uiState.value.links.isEmpty())
        }
    }

    @Test
    fun confirmRemove_successfullyRemovesItem() {
        runTest {
            val existing =
                TrackedLink(
                    id = 11L,
                    url = "https://example.com/one",
                    tags = listOf("one"),
                    filters = emptyList(),
                )
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(listOf(existing)))),
                    removeResult = SubscriptionsResult.Success(emptyList()),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onRemoveRequested(existing)
            viewModel.confirmRemove()
            advanceUntilIdle()

            assertEquals(1, repository.removeCalls)
            assertTrue(viewModel.uiState.value.links.isEmpty())
            assertEquals(null, viewModel.uiState.value.removeErrorMessage)
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)
        }
    }

    @Test
    fun confirmRemove_failureRestoresItemAndShowsError() {
        runTest {
            val existing =
                TrackedLink(
                    id = 12L,
                    url = "https://example.com/two",
                    tags = emptyList(),
                    filters = listOf("contains:kotlin"),
                )
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(listOf(existing)))),
                    removeResult =
                        SubscriptionsResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.Network,
                                    userMessage = "Сеть недоступна",
                                ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onRemoveRequested(existing)
            viewModel.confirmRemove()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, repository.removeCalls)
            assertEquals(1, state.links.size)
            assertEquals(existing.id, state.links.first().id)
            assertEquals("Сеть недоступна", state.removeErrorMessage)
            assertEquals(SubscriptionsScreenState.Content, state.screenState)
        }
    }

    @Test
    fun addSubscription_fromEmpty_transitionsToContent() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Success(
                            listOf(
                                TrackedLink(
                                    id = 100L,
                                    url = "https://example.dev/content",
                                    tags = listOf("dev"),
                                    filters = emptyList(),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)

            viewModel.onAddLinkInputChanged("https://example.dev/content")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(SubscriptionsScreenState.Content, viewModel.uiState.value.screenState)
            assertEquals(1, viewModel.uiState.value.links.size)
        }
    }

    @Test
    fun prepareFirstSubscriptionDraft_prefillsUrlInEmptyState() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)

            viewModel.prepareFirstSubscriptionDraft()

            assertEquals("https://", viewModel.uiState.value.addLinkInput)
        }
    }

    @Test
    fun refresh_whenRepositoryReturnsEmpty_keepsEmptyState() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Success(emptyList()),
                                SubscriptionsResult.Success(emptyList()),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)
            assertEquals(2, repository.calls)
            assertEquals(1, repository.forceRefreshCalls)
        }
    }

    @Test
    fun confirmRemove_whileRemoving_ignoresDuplicateRequest() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val existing =
                TrackedLink(
                    id = 13L,
                    url = "https://example.com/slow",
                    tags = emptyList(),
                    filters = emptyList(),
                )
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(listOf(existing)))),
                    removeResult = SubscriptionsResult.Success(emptyList()),
                    removeGate = gate,
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onRemoveRequested(existing)
            viewModel.confirmRemove()
            runCurrent()
            viewModel.confirmRemove()

            assertEquals(1, repository.removeCalls)
            gate.complete(Unit)
            advanceUntilIdle()
        }
    }

    private class FakeSubscriptionsRepository(
        private val results: ArrayDeque<SubscriptionsResult>,
        private val gate: CompletableDeferred<Unit>? = null,
        private val addResult: SubscriptionsResult = SubscriptionsResult.Success(emptyList()),
        private val removeResult: SubscriptionsResult = SubscriptionsResult.Success(emptyList()),
        private val removeGate: CompletableDeferred<Unit>? = null,
    ) : SubscriptionsRepository {
        var calls: Int = 0
            private set
        var addCalls: Int = 0
            private set
        var removeCalls: Int = 0
            private set

        var forceRefreshCalls: Int = 0
            private set

        override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult {
            calls += 1
            if (forceRefresh) {
                forceRefreshCalls += 1
            }
            gate?.await()
            return results.removeFirstOrNull() ?: SubscriptionsResult.Success(links = emptyList())
        }

        override suspend fun addSubscription(
            link: String,
            tags: List<String>,
            filters: List<String>,
        ): SubscriptionsResult {
            addCalls += 1
            return addResult
        }

        override suspend fun removeSubscription(link: String): SubscriptionsResult {
            removeCalls += 1
            removeGate?.await()
            return removeResult
        }
    }
}
