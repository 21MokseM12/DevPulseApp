package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import com.devpulse.app.domain.usecase.ApplyUpdatesFiltersUseCase
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_withNotifications_showsContentAndUnread() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    RemoteNotification(
                                        id = 1L,
                                        title = "Update",
                                        content = "Payload",
                                        link = "https://example.com/1",
                                        tags = listOf("updates"),
                                        isRead = false,
                                        updateOwner = "bot",
                                        creationDate = "2026-05-14T10:00:00Z",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 3),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(1, state.events.size)
            assertEquals(3, state.unreadCount)
            assertFalse(state.events.first().isRead)
            assertEquals(1778752800000L, state.events.first().receivedAtEpochMs)
        }
    }

    @Test
    fun init_feedFailure_showsErrorAndEmptyState() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.Network,
                                    userMessage = "Лента недоступна",
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 0),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.events.isEmpty())
            assertEquals("Лента недоступна", state.actionErrorMessage)
        }
    }

    @Test
    fun markAsRead_success_marksEventRead() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    RemoteNotification(
                                        id = 2L,
                                        title = "Update2",
                                        content = "Payload2",
                                        link = "https://example.com/2",
                                        tags = emptyList(),
                                        isRead = false,
                                        updateOwner = "bot",
                                        creationDate = "2026-05-14T10:00:00Z",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                    markReadResult = MarkReadResult.Success(updatedCount = 1),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.markAsRead(2L)
            advanceUntilIdle()

            assertEquals(listOf(2L), repository.lastMarkedIds)
            assertTrue(viewModel.uiState.value.events.first().isRead)
            assertEquals(1, viewModel.uiState.value.unreadCount)
            assertEquals(null, viewModel.uiState.value.actionErrorMessage)
        }
    }

    @Test
    fun markAsRead_failure_restoresUnreadAndShowsError() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    RemoteNotification(
                                        id = 3L,
                                        title = "Update3",
                                        content = "Payload3",
                                        link = "https://example.com/3",
                                        tags = emptyList(),
                                        isRead = false,
                                        updateOwner = "bot",
                                        creationDate = "2026-05-14T10:00:00Z",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 1),
                    markReadResult =
                        MarkReadResult.Failure(
                            error =
                                ApiError(
                                    kind = ApiErrorKind.BadRequest,
                                    userMessage = "Некорректный запрос",
                                ),
                        ),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.markAsRead(3L)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.events.first().isRead)
            assertEquals(1, viewModel.uiState.value.unreadCount)
            assertEquals("Не удалось отметить событие прочитанным.", viewModel.uiState.value.actionErrorMessage)
        }
    }

    @Test
    fun markAsRead_ignoresAlreadyReadEvent() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    RemoteNotification(
                                        id = 4L,
                                        title = "Read update",
                                        content = "Payload4",
                                        link = "https://example.com/4",
                                        tags = emptyList(),
                                        isRead = true,
                                        updateOwner = "bot",
                                        creationDate = "2026-05-14T10:00:00Z",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 0),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.markAsRead(4L)
            advanceUntilIdle()

            assertEquals(0, repository.markReadCalls)
            assertTrue(viewModel.uiState.value.events.first().isRead)
        }
    }

    @Test
    fun markAsRead_whileInProgress_ignoresDuplicateAction() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    RemoteNotification(
                                        id = 5L,
                                        title = "Slow update",
                                        content = "Payload5",
                                        link = "https://example.com/5",
                                        tags = emptyList(),
                                        isRead = false,
                                        updateOwner = "bot",
                                        creationDate = "2026-05-14T10:00:00Z",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 1),
                    markGate = gate,
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.markAsRead(5L)
            runCurrent()
            viewModel.markAsRead(5L)

            assertEquals(1, repository.markReadCalls)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.events.first().isRead)
        }
    }

    @Test
    fun onQueryChanged_appliesAfterDebounce() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 11L,
                                        title = "GitHub deploy",
                                        content = "Prod",
                                        source = "github",
                                    ),
                                    remoteNotification(
                                        id = 12L,
                                        title = "Jira issue",
                                        content = "Backlog",
                                        source = "jira",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.onQueryChanged("deploy")
            runCurrent()
            assertEquals(2, viewModel.uiState.value.events.size)

            advanceTimeBy(300L)
            runCurrent()
            assertEquals(listOf(11L), viewModel.uiState.value.events.map { it.id })
        }
    }

    @Test
    fun periodAndUnread_filtersCanBeCombined() {
        runTest {
            val today = LocalDate.now(ZoneOffset.UTC)
            val oldDay = today.minusDays(14)
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 21L,
                                        creationDate = today.atTime(9, 0).toInstant(ZoneOffset.UTC).toString(),
                                        isRead = false,
                                    ),
                                    remoteNotification(
                                        id = 22L,
                                        creationDate = today.atTime(8, 0).toInstant(ZoneOffset.UTC).toString(),
                                        isRead = true,
                                    ),
                                    remoteNotification(
                                        id = 23L,
                                        creationDate = oldDay.atTime(8, 0).toInstant(ZoneOffset.UTC).toString(),
                                        isRead = false,
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.onPeriodChanged(UpdatesPeriodFilter.TODAY)
            viewModel.onUnreadOnlyToggled()
            advanceUntilIdle()

            assertEquals(listOf(21L), viewModel.uiState.value.events.map { it.id })
        }
    }

    @Test
    fun sourceAndTagsSwitching_thenReset_restoresFullList() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 31L,
                                        source = "github",
                                        title = "Backend deploy",
                                        tags = listOf("backend"),
                                    ),
                                    remoteNotification(
                                        id = 32L,
                                        source = "github",
                                        title = "Frontend deploy",
                                        tags = listOf("frontend"),
                                    ),
                                    remoteNotification(
                                        id = 33L,
                                        source = "jira",
                                        title = "Issue update",
                                        tags = listOf("backend"),
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 3),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()
            assertEquals(listOf(31L, 32L, 33L), viewModel.uiState.value.events.map { it.id })

            viewModel.onSourceChanged("github")
            advanceUntilIdle()
            assertEquals(listOf(31L, 32L), viewModel.uiState.value.events.map { it.id })

            viewModel.onTagToggled("backend")
            advanceUntilIdle()
            assertEquals(listOf(31L), viewModel.uiState.value.events.map { it.id })
            assertTrue(viewModel.uiState.value.filterState.hasActiveFilters)

            viewModel.resetFilters()
            advanceUntilIdle()
            assertEquals(listOf(31L, 32L, 33L), viewModel.uiState.value.events.map { it.id })
            assertFalse(viewModel.uiState.value.filterState.hasActiveFilters)
            assertTrue(viewModel.uiState.value.filterState.selectedTags.isEmpty())
        }
    }

    @Test
    fun onPeriodAll_clearsLegacySourceAndLinkFilters_andRestoresFeed() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 34L,
                                        source = "github",
                                        link = "https://example.com/github",
                                    ),
                                    remoteNotification(
                                        id = 35L,
                                        source = "jira",
                                        link = "https://example.com/jira",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()
            assertEquals(listOf(34L, 35L), viewModel.uiState.value.events.map { it.id })

            viewModel.onSourceChanged("github")
            advanceUntilIdle()
            assertEquals(listOf(34L), viewModel.uiState.value.events.map { it.id })

            viewModel.onLinkFilterToggled("contains:kotlin")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.events.isEmpty())

            viewModel.onPeriodChanged(UpdatesPeriodFilter.ALL)
            advanceUntilIdle()

            assertEquals(listOf(34L, 35L), viewModel.uiState.value.events.map { it.id })
            assertEquals(null, viewModel.uiState.value.filterState.source)
            assertTrue(viewModel.uiState.value.filterState.selectedLinkFilters.isEmpty())
        }
    }

    @Test
    fun emptyResultAfterFilter_thenReset_clearsNoResultsState() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 41L,
                                        title = "GitHub deploy",
                                        source = "github",
                                        tags = listOf("backend"),
                                    ),
                                    remoteNotification(
                                        id = 42L,
                                        title = "Jira incident",
                                        source = "jira",
                                        tags = listOf("incident"),
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.onSourceChanged("github")
            viewModel.onTagToggled("incident")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.events.isEmpty())
            assertTrue(viewModel.uiState.value.filterState.hasActiveFilters)

            viewModel.resetFilters()
            advanceUntilIdle()
            assertEquals(listOf(41L, 42L), viewModel.uiState.value.events.map { it.id })
            assertFalse(viewModel.uiState.value.filterState.hasActiveFilters)
        }
    }

    @Test
    fun refresh_withSelectedTag_sendsTagToRepository() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 51L,
                                        source = "github",
                                        tags = listOf("backend"),
                                    ),
                                    remoteNotification(
                                        id = 52L,
                                        source = "jira",
                                        tags = listOf("incident"),
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()
            assertEquals(emptyList<String>(), repository.notificationRequests.last().tags)

            viewModel.onTagToggled("  BackEnd  ")
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf("backend"), repository.notificationRequests.last().tags)
            assertEquals(100, repository.notificationRequests.last().limit)
            assertEquals(0, repository.notificationRequests.last().offset)
        }
    }

    @Test
    fun onTagToggled_normalizesAndIgnoresBlankValues() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 81L,
                                        tags = listOf("backend"),
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 1),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.onTagToggled("  BackEnd ")
            advanceUntilIdle()
            assertEquals(setOf("backend"), viewModel.uiState.value.filterState.selectedTags)

            viewModel.onTagToggled("   ")
            advanceUntilIdle()
            assertEquals(setOf("backend"), viewModel.uiState.value.filterState.selectedTags)

            viewModel.onTagToggled("backend")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.filterState.selectedTags.isEmpty())
        }
    }

    @Test
    fun refresh_prunesSelectedTagsAndBuildsCaseInsensitiveAvailableTags() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(id = 82L, tags = listOf("Bug", "backend")),
                                    remoteNotification(id = 83L, tags = listOf("bug", "frontend")),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.onTagToggled("bug")
            viewModel.onTagToggled("frontend")
            advanceUntilIdle()
            assertEquals(setOf("bug", "frontend"), viewModel.uiState.value.filterState.selectedTags)
            assertEquals(listOf("backend", "Bug", "frontend"), viewModel.uiState.value.availableTags)

            repository.setNotificationsResult(
                NotificationsResult.Success(
                    notifications =
                        listOf(
                            remoteNotification(id = 84L, tags = listOf("Bug")),
                        ),
                ),
            )
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(setOf("bug"), viewModel.uiState.value.filterState.selectedTags)
            assertEquals(listOf("Bug"), viewModel.uiState.value.availableTags)
            assertEquals(listOf("bug", "frontend"), repository.notificationRequests.last().tags)
        }
    }

    @Test
    fun refresh_withExistingContent_togglesRefreshingStateWithoutBlockingList() {
        runTest {
            val refreshGate = CompletableDeferred<Unit>()
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 91L,
                                        title = "Initial",
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 1),
                    refreshGate = refreshGate,
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.refresh()
            runCurrent()

            val refreshingState = viewModel.uiState.value
            assertTrue(refreshingState.isRefreshing)
            assertFalse(refreshingState.isLoading)
            assertEquals(listOf(91L), refreshingState.events.map { it.id })

            refreshGate.complete(Unit)
            advanceUntilIdle()

            val completedState = viewModel.uiState.value
            assertFalse(completedState.isRefreshing)
            assertFalse(completedState.isLoading)
        }
    }

    @Test
    fun applyDigestContext_enablesUnreadOnlyFilter() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(id = 61L, isRead = false),
                                    remoteNotification(id = 62L, isRead = true),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 1),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.applyDigestContext(
                unreadOnly = true,
                periodStartEpochMs = null,
                periodEndEpochMs = null,
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.filterState.unreadOnly)
            assertEquals(listOf(61L), viewModel.uiState.value.events.map { it.id })
        }
    }

    @Test
    fun applyDigestContext_appliesUnreadAndDigestPeriodWindow() {
        runTest {
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 71L,
                                        creationDate = "2026-05-14T09:30:00Z",
                                        isRead = false,
                                    ),
                                    remoteNotification(
                                        id = 72L,
                                        creationDate = "2026-05-14T08:30:00Z",
                                        isRead = false,
                                    ),
                                    remoteNotification(
                                        id = 73L,
                                        creationDate = "2026-05-14T09:10:00Z",
                                        isRead = true,
                                    ),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val viewModel = UpdatesViewModel(repository, ApplyUpdatesFiltersUseCase())
            advanceUntilIdle()

            viewModel.applyDigestContext(
                unreadOnly = true,
                // 2026-05-14T08:40:00Z
                periodStartEpochMs = 1778750400000L,
                // 2026-05-14T09:10:00Z
                periodEndEpochMs = 1778752200000L,
            )
            advanceUntilIdle()

            assertEquals(listOf(71L), viewModel.uiState.value.events.map { it.id })
            assertEquals(1778750400000L, viewModel.uiState.value.filterState.periodStartEpochMs)
            assertEquals(1778752200000L, viewModel.uiState.value.filterState.periodEndEpochMs)
        }
    }

    @Test
    fun loadAndToggleLinkFilters_filtersBySubscriptionFilters() {
        runTest {
            val notificationsRepository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(id = 101L, link = "https://github.com/org/repo/pull/1"),
                                    remoteNotification(id = 102L, link = "https://github.com/org/repo/pull/2"),
                                ),
                        ),
                    unreadResult = UnreadCountResult.Success(unreadCount = 2),
                )
            val subscriptionsRepository =
                FakeSubscriptionsRepository(
                    SubscriptionsResult.Success(
                        links =
                            listOf(
                                TrackedLink(
                                    id = 1L,
                                    url = "https://github.com/org/repo/pull/1",
                                    tags = emptyList(),
                                    filters = listOf("contains:kotlin", "author:team"),
                                ),
                                TrackedLink(
                                    id = 2L,
                                    url = "https://github.com/org/repo/pull/2",
                                    tags = emptyList(),
                                    filters = listOf("contains:android"),
                                ),
                            ),
                    ),
                )
            val viewModel =
                UpdatesViewModel(
                    notificationsRepository = notificationsRepository,
                    subscriptionsRepository = subscriptionsRepository,
                    applyUpdatesFiltersUseCase = ApplyUpdatesFiltersUseCase(),
                )
            advanceUntilIdle()

            assertEquals(
                listOf("author:team", "contains:android", "contains:kotlin"),
                viewModel.uiState.value.availableLinkFilters,
            )
            assertEquals(listOf(101L, 102L), viewModel.uiState.value.events.map { it.id })

            viewModel.onLinkFilterToggled("  Contains:Kotlin ")
            advanceUntilIdle()
            assertEquals(setOf("contains:kotlin"), viewModel.uiState.value.filterState.selectedLinkFilters)
            assertEquals(listOf(101L), viewModel.uiState.value.events.map { it.id })
        }
    }

    private fun remoteNotification(
        id: Long,
        title: String = "Update$id",
        content: String = "Payload$id",
        source: String = "bot",
        link: String = "https://example.com/$id",
        creationDate: String = "2026-05-14T10:00:00Z",
        isRead: Boolean = false,
        tags: List<String> = emptyList(),
    ): RemoteNotification {
        return RemoteNotification(
            id = id,
            title = title,
            content = content,
            link = link,
            tags = tags,
            isRead = isRead,
            updateOwner = source,
            creationDate = creationDate,
        )
    }

    private class FakeSubscriptionsRepository(
        private var result: SubscriptionsResult,
    ) : SubscriptionsRepository {
        override suspend fun getSubscriptions(forceRefresh: Boolean): SubscriptionsResult = result

        override suspend fun addSubscription(
            link: String,
            tags: List<String>,
            filters: List<String>,
        ): SubscriptionsResult = result

        override suspend fun removeSubscription(link: String): SubscriptionsResult = result
    }

    private class FakeNotificationsRepository(
        private var notificationsResult: NotificationsResult,
        private val unreadResult: UnreadCountResult,
        private val markReadResult: MarkReadResult = MarkReadResult.Success(updatedCount = 1),
        private val markGate: CompletableDeferred<Unit>? = null,
        private val refreshGate: CompletableDeferred<Unit>? = null,
    ) : NotificationsRepository {
        var lastMarkedIds: List<Long>? = null
            private set
        var markReadCalls: Int = 0
            private set
        var notificationsCalls: Int = 0
            private set
        val notificationRequests = mutableListOf<NotificationRequest>()

        fun setNotificationsResult(result: NotificationsResult) {
            notificationsResult = result
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): NotificationsResult {
            notificationsCalls += 1
            if (notificationsCalls > 1) {
                refreshGate?.await()
            }
            notificationRequests += NotificationRequest(limit = limit, offset = offset, tags = tags)
            return notificationsResult
        }

        override suspend fun getUnreadCount(): UnreadCountResult = unreadResult

        override suspend fun markRead(notificationIds: List<Long>): MarkReadResult {
            markReadCalls += 1
            lastMarkedIds = notificationIds
            markGate?.await()
            return markReadResult
        }
    }

    private data class NotificationRequest(
        val limit: Int,
        val offset: Int,
        val tags: List<String>,
    )
}
