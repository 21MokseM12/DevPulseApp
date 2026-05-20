package com.devpulse.app.ui.subscriptions

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.SubscriptionsSortMode
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
    fun addSubscription_withUnsupportedDomain_showsDomainError() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://example.com/some/path")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(
                "Поддерживаются только ссылки на GitHub (github.com) и Stack Overflow (stackoverflow.com).",
                viewModel.uiState.value.addErrorMessage,
            )
            assertEquals(0, repository.addCalls)
        }
    }

    @Test
    fun addSubscription_withGithubUrl_passesValidation() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Success(
                            listOf(
                                TrackedLink(
                                    id = 1L,
                                    url = "https://github.com/owner/repo",
                                    tags = emptyList(),
                                    filters = emptyList(),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://github.com/owner/repo")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(1, repository.addCalls)
            assertEquals(null, viewModel.uiState.value.addErrorMessage)
        }
    }

    @Test
    fun addSubscription_withStackOverflowUrl_passesValidation() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Success(
                            listOf(
                                TrackedLink(
                                    id = 2L,
                                    url = "https://stackoverflow.com/questions/123",
                                    tags = emptyList(),
                                    filters = emptyList(),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://stackoverflow.com/questions/123")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(1, repository.addCalls)
            assertEquals(null, viewModel.uiState.value.addErrorMessage)
        }
    }

    @Test
    fun addSubscription_withGithubSubdomain_passesValidation() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                    addResult =
                        SubscriptionsResult.Success(
                            listOf(
                                TrackedLink(
                                    id = 3L,
                                    url = "https://gist.github.com/user/abc123",
                                    tags = emptyList(),
                                    filters = emptyList(),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://gist.github.com/user/abc123")
            viewModel.addSubscription()
            advanceUntilIdle()

            assertEquals(1, repository.addCalls)
            assertEquals(null, viewModel.uiState.value.addErrorMessage)
        }
    }

    @Test
    fun addSubscription_withUnsupportedDomain_doesNotCallRepository() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(emptyList()))),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://twitter.com/user/status/1")
            viewModel.addSubscription()
            advanceUntilIdle()

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
                                    url = "https://github.com/example/new",
                                    tags = listOf("dev"),
                                    filters = listOf("contains:kotlin"),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()

            viewModel.onAddLinkInputChanged("https://github.com/example/new")
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

            viewModel.onAddLinkInputChanged("https://github.com/example/existing")
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
                                    url = "https://stackoverflow.com/questions/100/content",
                                    tags = listOf("dev"),
                                    filters = emptyList(),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)

            viewModel.onAddLinkInputChanged("https://stackoverflow.com/questions/100/content")
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
    fun refresh_fromEmpty_whenNetworkInterrupted_showsErrorState() {
        runTest {
            val repository =
                FakeSubscriptionsRepository(
                    results =
                        ArrayDeque(
                            listOf(
                                SubscriptionsResult.Success(emptyList()),
                                SubscriptionsResult.Failure(
                                    error =
                                        ApiError(
                                            kind = ApiErrorKind.Network,
                                            userMessage = "Сеть недоступна",
                                        ),
                                ),
                            ),
                        ),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(SubscriptionsScreenState.Empty, viewModel.uiState.value.screenState)

            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(SubscriptionsScreenState.Error, state.screenState)
            assertEquals("Сеть недоступна", state.errorMessage)
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

    @Test
    fun buildTagGroups_groupsMultiTagLinksCaseInsensitively() {
        runTest {
            val viewModel = SubscriptionsViewModel(FakeSubscriptionsRepository(ArrayDeque()))
            val first =
                TrackedLink(
                    id = 1L,
                    url = "https://example.dev/first",
                    tags = listOf(" Dev ", "android", "dev"),
                    filters = emptyList(),
                )
            val second =
                TrackedLink(
                    id = 2L,
                    url = "https://example.dev/second",
                    tags = listOf("DEV"),
                    filters = emptyList(),
                )

            val groups =
                viewModel.buildTagGroups(
                    links = listOf(first, second),
                    sortMode = SubscriptionsSortMode.RECENTLY_ADDED,
                )

            assertEquals(listOf("#android", "#dev"), groups.map { it.title.lowercase() })
            assertEquals(listOf(1L), groups.first { it.title == "#android" }.items.map { it.id })
            assertEquals(
                listOf(2L, 1L),
                groups.first { it.title.lowercase() == "#dev" }.items.map { it.id },
            )
        }
    }

    @Test
    fun buildTagGroups_putsNoTagGroupLast() {
        runTest {
            val viewModel = SubscriptionsViewModel(FakeSubscriptionsRepository(ArrayDeque()))
            val tagged =
                TrackedLink(
                    id = 10L,
                    url = "https://example.dev/tagged",
                    tags = listOf("backend"),
                    filters = emptyList(),
                )
            val untagged =
                TrackedLink(
                    id = 11L,
                    url = "https://example.dev/untagged",
                    tags = listOf(" ", ""),
                    filters = emptyList(),
                )

            val groups =
                viewModel.buildTagGroups(
                    links = listOf(tagged, untagged),
                    sortMode = SubscriptionsSortMode.RECENTLY_ADDED,
                )

            assertEquals(listOf("#backend", "Без тегов"), groups.map { it.title })
            assertEquals(null, groups.last().tag)
            assertEquals(listOf(11L), groups.last().items.map { it.id })
        }
    }

    @Test
    fun buildTagGroups_keepsItemsOrderedBySelectedSortMode() {
        runTest {
            val viewModel = SubscriptionsViewModel(FakeSubscriptionsRepository(ArrayDeque()))
            val highId =
                TrackedLink(
                    id = 20L,
                    url = "https://example.dev/zeta",
                    tags = listOf("dev"),
                    filters = emptyList(),
                )
            val lowId =
                TrackedLink(
                    id = 5L,
                    url = "https://example.dev/alpha",
                    tags = listOf("dev"),
                    filters = emptyList(),
                )

            val recentGroups =
                viewModel.buildTagGroups(
                    links = listOf(lowId, highId),
                    sortMode = SubscriptionsSortMode.RECENTLY_ADDED,
                )
            val urlGroups =
                viewModel.buildTagGroups(
                    links = listOf(lowId, highId),
                    sortMode = SubscriptionsSortMode.URL_ASCENDING,
                )

            assertEquals(listOf(20L, 5L), recentGroups.single().items.map { it.id })
            assertEquals(listOf(5L, 20L), urlGroups.single().items.map { it.id })
        }
    }

    @Test
    fun confirmRemove_removesEmptyTagGroupFromGroupedLinks() {
        runTest {
            val onlyTagged =
                TrackedLink(
                    id = 31L,
                    url = "https://example.dev/only-dev",
                    tags = listOf("dev"),
                    filters = emptyList(),
                )
            val repository =
                FakeSubscriptionsRepository(
                    results = ArrayDeque(listOf(SubscriptionsResult.Success(listOf(onlyTagged)))),
                    removeResult = SubscriptionsResult.Success(emptyList()),
                )
            val viewModel = SubscriptionsViewModel(repository)
            advanceUntilIdle()
            assertEquals(listOf("#dev"), viewModel.uiState.value.groupedLinks.map { it.title })

            viewModel.onRemoveRequested(onlyTagged)
            viewModel.confirmRemove()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.groupedLinks.isEmpty())
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
