package com.devpulse.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.devpulse.app.BuildConfig
import com.devpulse.app.config.EnvironmentConfigProvider
import com.devpulse.app.data.local.db.CachedSessionEntity
import com.devpulse.app.data.local.db.SessionDao
import com.devpulse.app.data.local.preferences.UserSessionPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppBootstrapRepositoryTest {
    @Test
    fun loadBootstrapInfo_returnsNoCachedSession_whenDbAndPrefsAreEmpty() =
        runTest {
            val sessionDao = FakeSessionDao(initialSession = null)
            val userSessionPreferences = UserSessionPreferences(FakePreferencesDataStore())
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionDao = sessionDao,
                    userSessionPreferences = userSessionPreferences,
                )

            val info = repository.loadBootstrapInfo()

            assertEquals(BuildConfig.ENVIRONMENT, info.environment)
            assertEquals(BuildConfig.BASE_URL, info.baseUrl)
            assertFalse(info.hasCachedSession)
        }

    @Test
    fun loadBootstrapInfo_returnsCachedSession_whenDbSessionExists() =
        runTest {
            val sessionDao =
                FakeSessionDao(
                    initialSession =
                        CachedSessionEntity(
                            clientLogin = "moksem",
                            updatedAtEpochMs = 1_000L,
                        ),
                )
            val userSessionPreferences = UserSessionPreferences(FakePreferencesDataStore())
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionDao = sessionDao,
                    userSessionPreferences = userSessionPreferences,
                )

            val info = repository.loadBootstrapInfo()

            assertTrue(info.hasCachedSession)
        }

    @Test
    fun loadBootstrapInfo_returnsCachedSession_whenPreferenceLoginExists() =
        runTest {
            val dataStore = FakePreferencesDataStore()
            val userSessionPreferences = UserSessionPreferences(dataStore)
            userSessionPreferences.saveClientLogin("moksem")
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionDao = FakeSessionDao(initialSession = null),
                    userSessionPreferences = userSessionPreferences,
                )

            val info = repository.loadBootstrapInfo()

            assertTrue(info.hasCachedSession)
        }

    @Test
    fun loadBootstrapInfo_returnsNoCachedSession_whenPreferenceLoginIsBlank() =
        runTest {
            val dataStore = FakePreferencesDataStore()
            val userSessionPreferences = UserSessionPreferences(dataStore)
            userSessionPreferences.saveClientLogin("")
            val repository =
                DefaultAppBootstrapRepository(
                    configProvider = EnvironmentConfigProvider(),
                    sessionDao = FakeSessionDao(initialSession = null),
                    userSessionPreferences = userSessionPreferences,
                )

            val info = repository.loadBootstrapInfo()

            assertFalse(info.hasCachedSession)
        }

    private class FakeSessionDao(
        initialSession: CachedSessionEntity?,
    ) : SessionDao {
        private var cachedSession: CachedSessionEntity? = initialSession

        override suspend fun getSession(): CachedSessionEntity? = cachedSession

        override suspend fun upsert(session: CachedSessionEntity) {
            cachedSession = session
        }
    }

    private class FakePreferencesDataStore : DataStore<Preferences> {
        private var current: Preferences = emptyPreferences()

        override val data: Flow<Preferences>
            get() =
                flow {
                    emit(current)
                }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(current)
            current = updated
            return updated
        }
    }
}
