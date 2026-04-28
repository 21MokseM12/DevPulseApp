package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSessionPreferences
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val clientLogin: Flow<String?> =
            dataStore.data.map { preferences ->
                preferences[CLIENT_LOGIN_KEY]
            }

        suspend fun saveClientLogin(value: String) {
            dataStore.edit { preferences ->
                preferences[CLIENT_LOGIN_KEY] = value
            }
        }

        companion object {
            private val CLIENT_LOGIN_KEY = stringPreferencesKey("client_login")
        }
    }
