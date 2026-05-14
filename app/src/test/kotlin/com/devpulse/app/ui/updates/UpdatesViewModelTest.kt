package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
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
            val repository =
                FakeNotificationsRepository(
                    notificationsResult =
                        NotificationsResult.Success(
                            notifications =
                                listOf(
                                    remoteNotification(
                                        id = 21L,
                                        creationDate = "2026-05-14T09:00:00Z",
                                        isRead = false,
                                    ),
                                    remoteNotification(
                                        id = 22L,
                                        creationDate = "2026-05-14T08:00:00Z",
                                        isRead = true,
                                    ),
                                    remoteNotification(
                                        id = 23L,
                                        creationDate = "2026-05-01T08:00:00Z",
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

    private fun remoteNotification(
        id: Long,
        title: String = "Update$id",
        content: String = "Payload$id",
        source: String = "bot",
        creationDate: String = "2026-05-14T10:00:00Z",
        isRead: Boolean = false,
    ): RemoteNotification {
        return RemoteNotification(
            id = id,
            title = title,
            content = content,
            link = "https://example.com/$id",
            tags = emptyList(),
            isRead = isRead,
            updateOwner = source,
            creationDate = creationDate,
        )
    }

    private class FakeNotificationsRepository(
        private val notificationsResult: NotificationsResult,
        private val unreadResult: UnreadCountResult,
        private val markReadResult: MarkReadResult = MarkReadResult.Success(updatedCount = 1),
        private val markGate: CompletableDeferred<Unit>? = null,
    ) : NotificationsRepository {
        var lastMarkedIds: List<Long>? = null
            private set
        var markReadCalls: Int = 0
            private set

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): NotificationsResult = notificationsResult

        override suspend fun getUnreadCount(): UnreadCountResult = unreadResult

        override suspend fun markRead(notificationIds: List<Long>): MarkReadResult {
            markReadCalls += 1
            lastMarkedIds = notificationIds
            markGate?.await()
            return markReadResult
        }
    }
}
