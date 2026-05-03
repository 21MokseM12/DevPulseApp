package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface PushTokenStore {
    fun observeToken(): Flow<String?>

    suspend fun getToken(): String?

    suspend fun saveToken(token: String)

    suspend fun clearToken()
}

@Singleton
class DataStorePushTokenStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PushTokenStore {
        override fun observeToken(): Flow<String?> {
            return dataStore.data.map { preferences ->
                preferences[PUSH_TOKEN_KEY]
            }
        }

        override suspend fun getToken(): String? = observeToken().first()

        override suspend fun saveToken(token: String) {
            dataStore.edit { preferences ->
                preferences[PUSH_TOKEN_KEY] = token
            }
        }

        override suspend fun clearToken() {
            dataStore.edit { preferences ->
                preferences.remove(PUSH_TOKEN_KEY)
            }
        }

        private companion object {
            private val PUSH_TOKEN_KEY = stringPreferencesKey("push_token")
        }
    }
