package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface NotificationPermissionStore {
    fun observeHasRequested(): Flow<Boolean>

    suspend fun hasRequested(): Boolean

    suspend fun markRequested()

    suspend fun clearRequestedFlag()
}

@Singleton
class DataStoreNotificationPermissionStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : NotificationPermissionStore {
        override fun observeHasRequested(): Flow<Boolean> {
            return dataStore.data.map { preferences ->
                preferences[HAS_REQUESTED_KEY] ?: false
            }
        }

        override suspend fun hasRequested(): Boolean = observeHasRequested().first()

        override suspend fun markRequested() {
            dataStore.edit { preferences ->
                preferences[HAS_REQUESTED_KEY] = true
            }
        }

        override suspend fun clearRequestedFlag() {
            dataStore.edit { preferences ->
                preferences.remove(HAS_REQUESTED_KEY)
            }
        }

        private companion object {
            private val HAS_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
        }
    }
