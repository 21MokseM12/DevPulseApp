package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.RemoteNotification
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.UnreadCountResult
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
            val viewModel = UpdatesViewModel(repository)
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
            val viewModel = UpdatesViewModel(repository)
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
            val viewModel = UpdatesViewModel(repository)
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
            val viewModel = UpdatesViewModel(repository)
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
            val viewModel = UpdatesViewModel(repository)
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
            val viewModel = UpdatesViewModel(repository)
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
