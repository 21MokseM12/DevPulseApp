package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class StoredSession(
    val login: String,
    val isRegistered: Boolean,
    val updatedAtEpochMs: Long,
)

interface SessionStore {
    fun observeSession(): Flow<StoredSession?>

    fun observeClientLogin(): Flow<String?>

    suspend fun getSession(): StoredSession?

    suspend fun saveSession(
        login: String,
        isRegistered: Boolean = true,
    )

    suspend fun clearSession()
}

@Singleton
class DataStoreSessionStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SessionStore {
        override fun observeSession(): Flow<StoredSession?> {
            return dataStore.data.map { preferences ->
                val login = preferences[CLIENT_LOGIN_KEY]
                if (login.isNullOrBlank()) {
                    null
                } else {
                    StoredSession(
                        login = login,
                        isRegistered = preferences[IS_REGISTERED_KEY] ?: false,
                        updatedAtEpochMs = preferences[UPDATED_AT_KEY] ?: 0L,
                    )
                }
            }
        }

        override fun observeClientLogin(): Flow<String?> {
            return dataStore.data.map { preferences ->
                preferences[CLIENT_LOGIN_KEY]
            }
        }

        override suspend fun getSession(): StoredSession? = observeSession().first()

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            dataStore.edit { preferences ->
                preferences[CLIENT_LOGIN_KEY] = login
                preferences[IS_REGISTERED_KEY] = isRegistered
                preferences[UPDATED_AT_KEY] = System.currentTimeMillis()
            }
        }

        override suspend fun clearSession() {
            dataStore.edit { preferences ->
                preferences.remove(CLIENT_LOGIN_KEY)
                preferences.remove(IS_REGISTERED_KEY)
                preferences.remove(UPDATED_AT_KEY)
            }
        }

        companion object {
            private val CLIENT_LOGIN_KEY = stringPreferencesKey("client_login")
            private val IS_REGISTERED_KEY = booleanPreferencesKey("session_registered")
            private val UPDATED_AT_KEY = longPreferencesKey("session_updated_at")
        }
    }
