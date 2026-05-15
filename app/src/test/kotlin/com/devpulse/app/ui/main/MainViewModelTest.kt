package com.devpulse.app.ui.main

import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.ui.auth.AuthAction
import com.devpulse.app.ui.auth.AuthSuccessEvent
import kotlinx.coroutines.CompletableDeferred
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

            val viewModel =
                MainViewModel(
                    repository,
                    FakeSessionStore(
                        initialSession =
                            StoredSession(
                                login = "demo-user",
                                isRegistered = true,
                                updatedAtEpochMs = 1_000L,
                            ),
                    ),
                    createAccountLifecycleUseCase(),
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("debug", state.environment)
            assertEquals("https://api.example.com/", state.baseUrl)
            assertFalse(state.isBootstrapping)
            assertTrue(state.hasCachedSession)
            assertEquals(StartupDestination.Subscriptions, state.startupDestination)
        }
    }

    @Test
    fun init_withoutSession_routesToAuth() {
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
            val viewModel = MainViewModel(repository, FakeSessionStore(), createAccountLifecycleUseCase())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)
        }
    }

    @Test
    fun onAuthSucceeded_setsHasCachedSessionToTrue() {
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
            val viewModel = MainViewModel(repository, sessionStore, createAccountLifecycleUseCase())
            advanceUntilIdle()
            viewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Login,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasCachedSession)
            assertEquals("moksem", sessionStore.getSession()?.login)
            assertEquals(StartupDestination.Subscriptions, viewModel.uiState.value.startupDestination)
        }
    }

    @Test
    fun backgroundDuringAuthSuccess_navigatesToMainAfterResume() {
        runTest {
            val saveGate = CompletableDeferred<Unit>()
            val repository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        ),
                )
            val sessionStore = FakeSessionStore(saveGate = saveGate)
            val viewModel = MainViewModel(repository, sessionStore, createAccountLifecycleUseCase())
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)

            viewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Register,
                ),
            )
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)

            saveGate.complete(Unit)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Subscriptions, viewModel.uiState.value.startupDestination)
        }
    }

    @Test
    fun rotationDuringAuthSuccess_keepsMainNavigationAfterRecreation() {
        runTest {
            val saveGate = CompletableDeferred<Unit>()
            val sharedSessionStore = FakeSessionStore(saveGate = saveGate)
            val initialRepository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        ),
                )
            val firstViewModel =
                MainViewModel(
                    initialRepository,
                    sharedSessionStore,
                    createAccountLifecycleUseCase(sessionStore = sharedSessionStore),
                )
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, firstViewModel.uiState.value.startupDestination)

            firstViewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Login,
                ),
            )
            advanceUntilIdle()

            val recreatedViewModel =
                MainViewModel(
                    initialRepository,
                    sharedSessionStore,
                    createAccountLifecycleUseCase(sessionStore = sharedSessionStore),
                )
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, recreatedViewModel.uiState.value.startupDestination)

            saveGate.complete(Unit)
            advanceUntilIdle()

            assertTrue(recreatedViewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Subscriptions, recreatedViewModel.uiState.value.startupDestination)
        }
    }

    @Test
    fun quickRelaunch_afterAuthSuccess_keepsMainDestination() {
        runTest {
            val sharedSessionStore = FakeSessionStore()
            val initialRepository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        ),
                )
            val firstViewModel =
                MainViewModel(
                    initialRepository,
                    sharedSessionStore,
                    createAccountLifecycleUseCase(sessionStore = sharedSessionStore),
                )
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, firstViewModel.uiState.value.startupDestination)

            firstViewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Register,
                ),
            )

            val relaunchedRepository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        ),
                )
            val relaunchedViewModel =
                MainViewModel(
                    relaunchedRepository,
                    sharedSessionStore,
                    createAccountLifecycleUseCase(sessionStore = sharedSessionStore),
                )
            advanceUntilIdle()

            assertTrue(relaunchedViewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Subscriptions, relaunchedViewModel.uiState.value.startupDestination)
            assertEquals("moksem", sharedSessionStore.getSession()?.login)
        }
    }

    @Test
    fun registerSuccess_navigatesToMain_smoke() {
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
            val viewModel =
                MainViewModel(
                    repository,
                    sessionStore,
                    createAccountLifecycleUseCase(sessionStore = sessionStore),
                )
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)

            viewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "new-user",
                    action = AuthAction.Register,
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasCachedSession)
            assertEquals(StartupDestination.Subscriptions, viewModel.uiState.value.startupDestination)
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
            val viewModel =
                MainViewModel(
                    repository,
                    sessionStore,
                    createAccountLifecycleUseCase(sessionStore = sessionStore),
                )
            advanceUntilIdle()
            viewModel.onLogout()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertEquals(null, sessionStore.getSession())
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)
        }
    }

    @Test
    fun loginLogoutLogin_cycle_keepsStartupDestinationConsistent() {
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
            val viewModel =
                MainViewModel(
                    repository,
                    sessionStore,
                    createAccountLifecycleUseCase(sessionStore = sessionStore),
                )
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)

            viewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Login,
                ),
            )
            advanceUntilIdle()
            assertEquals(StartupDestination.Subscriptions, viewModel.uiState.value.startupDestination)

            viewModel.onLogout()
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)

            viewModel.onAuthSucceeded(
                AuthSuccessEvent(
                    login = "moksem",
                    action = AuthAction.Login,
                ),
            )
            advanceUntilIdle()
            assertEquals(StartupDestination.Subscriptions, viewModel.uiState.value.startupDestination)
            assertTrue(viewModel.uiState.value.hasCachedSession)
        }
    }

    @Test
    fun logoutDuringBootstrap_keepsAuthRouteAfterBootstrapFinishes() {
        runTest {
            val bootstrapGate = CompletableDeferred<Unit>()
            val repository =
                FakeAppBootstrapRepository(
                    info =
                        AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = true,
                        ),
                    gate = bootstrapGate,
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
            val viewModel =
                MainViewModel(
                    repository,
                    sessionStore,
                    createAccountLifecycleUseCase(sessionStore = sessionStore),
                )
            advanceUntilIdle()

            viewModel.onLogout()
            advanceUntilIdle()
            bootstrapGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasCachedSession)
            assertFalse(viewModel.uiState.value.isBootstrapping)
            assertEquals(StartupDestination.Auth, viewModel.uiState.value.startupDestination)
        }
    }

    private class FakeAppBootstrapRepository(
        private val info: AppBootstrapInfo,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : AppBootstrapRepository {
        override suspend fun loadBootstrapInfo(): AppBootstrapInfo {
            gate?.await()
            return info
        }
    }

    private fun createAccountLifecycleUseCase(
        sessionStore: SessionStore = FakeSessionStore(),
    ): AccountLifecycleUseCase {
        return AccountLifecycleUseCase(
            remoteDataSource = FakeRemoteDataSource(),
            sessionStore = sessionStore,
            updatesRepository = FakeUpdatesRepository(),
            pushTokenStore = FakePushTokenStore(),
            notificationPermissionStore = FakeNotificationPermissionStore(),
        )
    }

    private class FakeSessionStore(
        initialSession: StoredSession? = null,
        private val saveGate: CompletableDeferred<Unit>? = null,
    ) : SessionStore {
        private val state = MutableStateFlow(initialSession)

        override fun observeSession(): Flow<StoredSession?> = state

        override fun observeClientLogin(): Flow<String?> = state.map { session -> session?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            saveGate?.await()
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

    private class FakeRemoteDataSource : DevPulseRemoteDataSource {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return RemoteCallResult.Success(Unit, 200)
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return RemoteCallResult.Success(emptyList(), 200)
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            error("Not used in MainViewModel tests")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("Not used in MainViewModel tests")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            error("Not used in MainViewModel tests")
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            error("Not used in MainViewModel tests")
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            error("Not used in MainViewModel tests")
        }
    }

    private class FakeUpdatesRepository : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = MutableStateFlow(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class FakePushTokenStore : PushTokenStore {
        override fun observeToken(): Flow<String?> = MutableStateFlow(null)

        override suspend fun getToken(): String? = null

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearToken() = Unit
    }

    private class FakeNotificationPermissionStore : NotificationPermissionStore {
        override fun observeHasRequested(): Flow<Boolean> = MutableStateFlow(false)

        override suspend fun hasRequested(): Boolean = false

        override suspend fun markRequested() = Unit

        override suspend fun clearRequestedFlag() = Unit
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
