package com.devpulse.app.ui.main

import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsBootstrapInfoIntoUiState() {
        runTest {
            val repository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = true,
                        ),
                )

            val viewModel = MainViewModel(repository, FakeSessionStore())
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("debug", state.environment)
            assertEquals("https://api.example.com/", state.baseUrl)
            assertFalse(state.isBootstrapping)
            assertTrue(state.hasCachedSession)
        }
    }

    @Test
    fun onLoginSucceeded_setsHasCachedSessionToTrue() {
        runTest {
            val repository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        ),
                )

            val sessionStore = FakeSessionStore()
            val viewModel = MainViewModel(repository, sessionStore)
            advanceUntilIdle()
            viewModel.onLoginSucceeded(login = "moksem")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasCachedSession)
            assertEquals("moksem", sessionStore.getSession()?.login)
        }
    }

    @Test
    fun onLogout_setsHasCachedSessionToFalse() {
        runTest {
            val repository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = true,
                        ),
                )

            val sessionStore =
                FakeSessionStore(
                    initialSession =
                        StoredSession(
                            login = "demo-user",
                            isRegistered = true,
                            updatedAtEpochMs = 1_000L,
                        ),
                )
            val viewModel = MainViewModel(repository, sessionStore)
            advanceUntilIdle()
            viewModel.onLogout()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertEquals(null, sessionStore.getSession())
        }
    }

    private class FakeAppBootstrapRepository(
        private val info: AppBootstrapInfo,
    ) : AppBootstrapRepository {
        override suspend fun loadBootstrapInfo(): AppBootstrapInfo = info
    }

    private class FakeSessionStore(
        initialSession: StoredSession? = null,
    ) : SessionStore {
        private val state = MutableStateFlow(initialSession)

        override fun observeSession(): Flow<StoredSession?> = state

        override fun observeClientLogin(): Flow<String?> = state.map { session -> session?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            state.value =
                StoredSession(
                    login = login,
                    isRegistered = isRegistered,
                    updatedAtEpochMs = 1_000L,
                )
        }

        override suspend fun clearSession() {
            state.value = null
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
