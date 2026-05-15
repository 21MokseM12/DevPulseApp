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
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.repository.AuthRepository
import com.devpulse.app.domain.repository.AuthResult
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.domain.usecase.AccountLifecycleUseCase
import com.devpulse.app.domain.usecase.LoginClientUseCase
import com.devpulse.app.domain.usecase.RegisterClientUseCase
import com.devpulse.app.push.ParsedPushUpdate
import com.devpulse.app.ui.auth.AuthAction
import com.devpulse.app.ui.auth.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLifecycleRegressionIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun registerLogoutLogin_chain_reachesSubscriptionsWithoutRegression() =
        runTest {
            val authRepository =
                SequenceAuthRepository(
                    loginResults = ArrayDeque(listOf(AuthResult.Success)),
                    registerResults = ArrayDeque(listOf(AuthResult.Success)),
                )
            val sessionStore = InMemorySessionStore()
            val mainViewModel = createMainViewModel(sessionStore)
            val authViewModel = createAuthViewModel(authRepository)
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, mainViewModel.uiState.value.startupDestination)

            authViewModel.onLoginChanged("moksem")
            authViewModel.onPasswordChanged("secret")
            authViewModel.submitRegister()
            advanceUntilIdle()

            val registerSuccess = authViewModel.uiState.value.pendingAuthSuccess
            assertNotNull(registerSuccess)
            assertEquals(AuthAction.Register, registerSuccess?.action)
            mainViewModel.onAuthSucceeded(requireNotNull(registerSuccess))
            authViewModel.onAuthSuccessHandled()
            advanceUntilIdle()
            assertEquals(StartupDestination.Subscriptions, mainViewModel.uiState.value.startupDestination)

            mainViewModel.onLogout()
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, mainViewModel.uiState.value.startupDestination)
            assertNull(sessionStore.getSession())

            authViewModel.onPasswordChanged("secret")
            authViewModel.submitLogin()
            advanceUntilIdle()

            val loginSuccess = authViewModel.uiState.value.pendingAuthSuccess
            assertNotNull(loginSuccess)
            assertEquals(AuthAction.Login, loginSuccess?.action)
            mainViewModel.onAuthSucceeded(requireNotNull(loginSuccess))
            authViewModel.onAuthSuccessHandled()
            advanceUntilIdle()
            assertEquals(StartupDestination.Subscriptions, mainViewModel.uiState.value.startupDestination)
            assertTrue(mainViewModel.uiState.value.hasCachedSession)
        }

    @Test
    fun reloginFailure_afterLogout_keepsAuthDestinationAndShowsMappedError() =
        runTest {
            val authRepository =
                SequenceAuthRepository(
                    loginResults =
                        ArrayDeque(
                            listOf(
                                AuthResult.Failure(
                                    error =
                                        ApiError(
                                            kind = ApiErrorKind.NotFound,
                                            userMessage = "Endpoint not found",
                                        ),
                                ),
                            ),
                        ),
                )
            val sessionStore = InMemorySessionStore()
            val mainViewModel = createMainViewModel(sessionStore)
            val authViewModel = createAuthViewModel(authRepository)
            advanceUntilIdle()

            mainViewModel.onLogout()
            advanceUntilIdle()
            assertEquals(StartupDestination.Auth, mainViewModel.uiState.value.startupDestination)

            authViewModel.onLoginChanged("moksem")
            authViewModel.onPasswordChanged("secret")
            authViewModel.submitLogin()
            advanceUntilIdle()

            assertNull(authViewModel.uiState.value.pendingAuthSuccess)
            assertEquals(
                "Не удалось войти. Сервис авторизации временно недоступен. Повторите попытку позже.",
                authViewModel.uiState.value.activeErrorMessage,
            )
            assertEquals(StartupDestination.Auth, mainViewModel.uiState.value.startupDestination)
            assertNull(sessionStore.getSession())
        }

    private fun createAuthViewModel(repository: AuthRepository): AuthViewModel {
        return AuthViewModel(
            loginClientUseCase = LoginClientUseCase(repository),
            registerClientUseCase = RegisterClientUseCase(repository),
        )
    }

    private fun createMainViewModel(sessionStore: SessionStore): MainViewModel {
        return MainViewModel(
            appBootstrapRepository =
                object : AppBootstrapRepository {
                    override suspend fun loadBootstrapInfo(): AppBootstrapInfo {
                        return AppBootstrapInfo(
                            environment = "debug",
                            baseUrl = "https://api.example.com/",
                            hasCachedSession = false,
                        )
                    }
                },
            sessionStore = sessionStore,
            accountLifecycleUseCase =
                AccountLifecycleUseCase(
                    remoteDataSource = NoopRemoteDataSource(),
                    sessionStore = sessionStore,
                    updatesRepository = NoopUpdatesRepository(),
                    pushTokenStore = NoopPushTokenStore(),
                    notificationPermissionStore = NoopNotificationPermissionStore(),
                ),
        )
    }

    private class SequenceAuthRepository(
        private val loginResults: ArrayDeque<AuthResult> = ArrayDeque(),
        private val registerResults: ArrayDeque<AuthResult> = ArrayDeque(),
    ) : AuthRepository {
        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            return loginResults.removeFirstOrNull() ?: AuthResult.Success
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            return registerResults.removeFirstOrNull() ?: AuthResult.Success
        }
    }

    private class InMemorySessionStore : SessionStore {
        private val state = MutableStateFlow<StoredSession?>(null)

        override fun observeSession(): Flow<StoredSession?> = state.asStateFlow()

        override fun observeClientLogin(): Flow<String?> = state.asStateFlow().map { it?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            state.value =
                StoredSession(
                    login = login,
                    isRegistered = isRegistered,
                    updatedAtEpochMs = 1L,
                )
        }

        override suspend fun clearSession() {
            state.value = null
        }
    }

    private class NoopRemoteDataSource : DevPulseRemoteDataSource {
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
            error("Not used in lifecycle tests")
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            error("Not used in lifecycle tests")
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            error("Not used in lifecycle tests")
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            error("Not used in lifecycle tests")
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            error("Not used in lifecycle tests")
        }
    }

    private class NoopUpdatesRepository : UpdatesRepository {
        override fun observeUpdates(): Flow<List<UpdateEvent>> = MutableStateFlow(emptyList())

        override suspend fun saveIncomingUpdate(
            update: ParsedPushUpdate,
            receivedAtEpochMs: Long,
        ): Boolean = true

        override suspend fun markAsRead(updateId: Long): Boolean = true

        override suspend fun clearUpdates() = Unit
    }

    private class NoopPushTokenStore : PushTokenStore {
        override fun observeToken(): Flow<String?> = MutableStateFlow(null)

        override suspend fun getToken(): String? = null

        override suspend fun saveToken(token: String) = Unit

        override suspend fun clearToken() = Unit
    }

    private class NoopNotificationPermissionStore : NotificationPermissionStore {
        override fun observeHasRequested(): Flow<Boolean> = MutableStateFlow(false)

        override suspend fun hasRequested(): Boolean = false

        override suspend fun markRequested() = Unit

        override suspend fun clearRequestedFlag() = Unit
    }
}
