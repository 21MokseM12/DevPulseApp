package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationPresentationMode {
    Compact,
    Detailed,
}

data class NotificationPreferences(
    val enabled: Boolean = true,
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
)

interface NotificationPreferencesStore {
    fun observePreferences(): Flow<NotificationPreferences>

    suspend fun getPreferences(): NotificationPreferences

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setPresentationMode(mode: NotificationPresentationMode)

    suspend fun reset()
}

@Singleton
class DataStoreNotificationPreferencesStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : NotificationPreferencesStore {
        override fun observePreferences(): Flow<NotificationPreferences> {
            return dataStore.data.map { preferences ->
                NotificationPreferences(
                    enabled = preferences[ENABLED_KEY] ?: true,
                    presentationMode = parseMode(preferences[PRESENTATION_MODE_KEY]),
                )
            }
        }

        override suspend fun getPreferences(): NotificationPreferences = observePreferences().first()

        override suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[ENABLED_KEY] = enabled
            }
        }

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) {
            dataStore.edit { preferences ->
                preferences[PRESENTATION_MODE_KEY] = mode.name
            }
        }

        override suspend fun reset() {
            dataStore.edit { preferences ->
                preferences.remove(ENABLED_KEY)
                preferences.remove(PRESENTATION_MODE_KEY)
            }
        }

        private fun parseMode(rawValue: String?): NotificationPresentationMode {
            return NotificationPresentationMode.entries.firstOrNull { it.name == rawValue }
                ?: NotificationPresentationMode.Detailed
        }

        private companion object {
            private val ENABLED_KEY = booleanPreferencesKey("notification_preferences_enabled")
            private val PRESENTATION_MODE_KEY = stringPreferencesKey("notification_preferences_presentation_mode")
        }
    }
