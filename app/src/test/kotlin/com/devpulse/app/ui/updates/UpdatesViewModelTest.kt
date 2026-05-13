package com.devpulse.app.ui.updates

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Test
    fun markAsRead_ignoresAlreadyReadEvent() {
        runTest {
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 4L,
                                remoteEventId = "evt-4",
                                linkUrl = "https://example.com/4",
                                title = "Read update",
                                content = "Payload4",
                                receivedAtEpochMs = 4000L,
                                isRead = true,
                            ),
                        ),
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            viewModel.markAsRead(4L)
            advanceUntilIdle()

            assertEquals(0, repository.markAsReadCalls)
            assertTrue(viewModel.uiState.value.events.first().isRead)
        }
    }

    @Test
    fun markAsRead_whileInProgress_ignoresDuplicateAction() {
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 5L,
                                remoteEventId = "evt-5",
                                linkUrl = "https://example.com/5",
                                title = "Slow update",
                                content = "Payload5",
                                receivedAtEpochMs = 5000L,
                                isRead = false,
                            ),
                        ),
                    markGate = gate,
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            viewModel.markAsRead(5L)
            runCurrent()
            viewModel.markAsRead(5L)

            assertEquals(1, repository.markAsReadCalls)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.events.first().isRead)
        }
    }

    @Test
    fun newUpdatesEmission_clearsPreviousActionError() {
        runTest {
            val repository =
                FakeUpdatesRepository(
                    initial =
                        listOf(
                            UpdateEvent(
                                id = 6L,
                                remoteEventId = "evt-6",
                                linkUrl = "https://example.com/6",
                                title = "Update6",
                                content = "Payload6",
                                receivedAtEpochMs = 6000L,
                                isRead = false,
                            ),
                        ),
                    markAsReadResult = false,
                )
            val viewModel = UpdatesViewModel(repository)
            advanceUntilIdle()

            viewModel.markAsRead(6L)
            advanceUntilIdle()
            assertEquals("Не удалось отметить событие прочитанным.", viewModel.uiState.value.actionErrorMessage)

            repository.emitUpdates(
                listOf(
                    UpdateEvent(
                        id = 7L,
                        remoteEventId = "evt-7",
                        linkUrl = "https://example.com/7",
                        title = "Update7",
                        content = "Payload7",
                        receivedAtEpochMs = 7000L,
                        isRead = false,
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.actionErrorMessage)
            assertEquals(7L, viewModel.uiState.value.events.first().id)
        }
    }

    private class FakeUpdatesRepository(
        initial: List<UpdateEvent>,
        private val markAsReadResult: Boolean = true,
        private val markGate: CompletableDeferred<Unit>? = null,
    ) : UpdatesRepository {
        private val updates = MutableStateFlow(initial)
        var lastMarkedId: Long? = null
            private set
        var markAsReadCalls: Int = 0
            private set

        override fun observeUpdates(): Flow<List<UpdateEvent>> = updates.asStateFlow()

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean {
            return true
        }

        override suspend fun markAsRead(updateId: Long): Boolean {
            markAsReadCalls += 1
            lastMarkedId = updateId
            markGate?.await()
            if (markAsReadResult) {
                updates.value =
                    updates.value.map { event ->
                        if (event.id == updateId) event.copy(isRead = true) else event
                    }
            }
            return markAsReadResult
        }

        override suspend fun clearUpdates() {
            updates.value = emptyList()
        }

        fun emitUpdates(value: List<UpdateEvent>) {
            updates.value = value
        }
    }
}
