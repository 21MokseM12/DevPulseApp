package com.devpulse.app.ui.main

import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun init_loadsBootstrapInfoIntoUiState() =
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

            val viewModel = MainViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("debug", state.environment)
            assertEquals("https://api.example.com/", state.baseUrl)
            assertFalse(state.isBootstrapping)
            assertTrue(state.hasCachedSession)
        }

    @Test
    fun onLoginSucceeded_setsHasCachedSessionToTrue() =
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

            val viewModel = MainViewModel(repository)
            advanceUntilIdle()
            viewModel.onLoginSucceeded()

            assertTrue(viewModel.uiState.value.hasCachedSession)
        }

    @Test
    fun onLogout_setsHasCachedSessionToFalse() =
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

            val viewModel = MainViewModel(repository)
            advanceUntilIdle()
            viewModel.onLogout()

            assertFalse(viewModel.uiState.value.hasCachedSession)
        }

    private class FakeAppBootstrapRepository(
        private val info: AppBootstrapInfo,
    ) : AppBootstrapRepository {
        override suspend fun loadBootstrapInfo(): AppBootstrapInfo = info
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
