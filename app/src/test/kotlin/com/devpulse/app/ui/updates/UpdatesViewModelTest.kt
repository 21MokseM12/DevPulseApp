package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun init_withEvents_showsContent() {
        runTest {
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 1L,
                                remoteEventId = "evt-1",
                                linkUrl = "https://example.com/1",
                                title = "Update",
                                content = "Payload",
                                receivedAtEpochMs = 1000L,
                                isRead = false,
                            ),
                        ),
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(1, state.events.size)
            assertFalse(state.events.first().isRead)
        }
    }

    @Test
    fun markAsRead_success_marksEventRead() {
        runTest {
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 2L,
                                remoteEventId = "evt-2",
                                linkUrl = "https://example.com/2",
                                title = "Update2",
                                content = "Payload2",
                                receivedAtEpochMs = 2000L,
                                isRead = false,
                            ),
                        ),
                    markAsReadResult = true,
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            viewModel.markAsRead(2L)
            advanceUntilIdle()

            assertEquals(2L, repository.lastMarkedId)
            assertTrue(viewModel.uiState.value.events.first().isRead)
            assertEquals(null, viewModel.uiState.value.actionErrorMessage)
        }
    }

    @Test
    fun markAsRead_failure_restoresUnreadAndShowsError() {
        runTest {
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 3L,
                                remoteEventId = "evt-3",
                                linkUrl = "https://example.com/3",
                                title = "Update3",
                                content = "Payload3",
                                receivedAtEpochMs = 3000L,
                                isRead = false,
                            ),
                        ),
                    markAsReadResult = false,
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            viewModel.markAsRead(3L)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.events.first().isRead)
            assertEquals("Не удалось отметить событие прочитанным.", viewModel.uiState.value.actionErrorMessage)
        }
    }

    private class FakeUpdatesRepository(
        initial: List<UpdateEvent>,
        private val markAsReadResult: Boolean = true,
    ) : UpdatesRepository {
        private val updates = MutableStateFlow(initial)
        var lastMarkedId: Long? = null
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = updates.asStateFlow()

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            return true
        }

        override suspend fun markAsRead(updateId: Long): Boolean {
            lastMarkedId = updateId
            if (markAsReadResult) {
                updates.value =
                    updates.value.map { event ->
                        if (event.id == updateId) event.copy(isRead = true) else event
                    }
            }
            return markAsReadResult
        }
    }
}
