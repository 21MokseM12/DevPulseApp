package com.devpulse.app.data.repository

import com.devpulse.app.BuildConfig
import com.devpulse.app.config.EnvironmentConfigProvider
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppBootstrapRepositoryTest {
    @Test
    fun loadBootstrapInfo_returnsNoCachedSession_whenSessionIsEmpty() {
        runTest {
            val sessionStore = FakeSessionStore(initialSession = null)
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionStore = sessionStore,
                )

            val info = repository.loadBootstrapInfo()

            assertEquals(BuildConfig.ENVIRONMENT, info.environment)
            assertEquals(BuildConfig.BASE_URL, info.baseUrl)
            assertFalse(info.hasCachedSession)
        }
    }

    @Test
    fun loadBootstrapInfo_returnsCachedSession_whenSessionExists() {
        runTest {
            val sessionStore =
                FakeSessionStore(
                    initialSession =
                        StoredSession(
                            login = "moksem",
                            isRegistered = true,
                            updatedAtEpochMs = 1_000L,
                        ),
                )
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionStore = sessionStore,
                )

            val info = repository.loadBootstrapInfo()

            assertTrue(info.hasCachedSession)
        }
    }

    @Test
    fun loadBootstrapInfo_returnsNoCachedSession_afterClearSession() {
        runTest {
            val sessionStore =
                FakeSessionStore(
                    initialSession =
                        StoredSession(
                            login = "moksem",
                            isRegistered = true,
                            updatedAtEpochMs = 1_000L,
                        ),
                )
            sessionStore.clearSession()
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionStore = sessionStore,
                )

            val info = repository.loadBootstrapInfo()

            assertFalse(info.hasCachedSession)
        }
    }

    private class FakeSessionStore(initialSession: StoredSession?) : SessionStore {
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
