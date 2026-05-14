package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationPresentationMode {
    Compact,
    Detailed,
}

enum class NotificationDigestMode {
    Daily,
}

data class NotificationPreferences(
    val enabled: Boolean = true,
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
    val digestMode: NotificationDigestMode? = null,
)

interface NotificationPreferencesStore {
    fun observePreferences(): Flow<NotificationPreferences>

    suspend fun getPreferences(): NotificationPreferences

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setPresentationMode(mode: NotificationPresentationMode)

    suspend fun setDigestMode(mode: NotificationDigestMode?)

    suspend fun reset()
}

@Singleton
class DataStoreNotificationPreferencesStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : NotificationPreferencesStore {
        @Volatile
        private var inMemoryFallback: NotificationPreferences? = null

        override fun observePreferences(): Flow<NotificationPreferences> {
            return dataStore.data
                .catch { error ->
                    emit(emptyPreferences())
                }.map { preferences ->
                    inMemoryFallback ?: parsePreferences(preferences)
                }
        }

        override suspend fun getPreferences(): NotificationPreferences = observePreferences().first()

        override suspend fun setEnabled(enabled: Boolean) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(enabled = enabled)
            runCatching {
                dataStore.edit { preferences ->
                    preferences[ENABLED_KEY] = enabled
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(presentationMode = mode)
            runCatching {
                dataStore.edit { preferences ->
                    preferences[PRESENTATION_MODE_KEY] = mode.name
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun setDigestMode(mode: NotificationDigestMode?) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(digestMode = mode)
            runCatching {
                dataStore.edit { preferences ->
                    if (mode == null) {
                        preferences.remove(DIGEST_MODE_KEY)
                    } else {
                        preferences[DIGEST_MODE_KEY] = mode.name
                    }
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun reset() {
            runCatching {
                dataStore.edit { preferences ->
                    preferences.remove(ENABLED_KEY)
                    preferences.remove(PRESENTATION_MODE_KEY)
                    preferences.remove(DIGEST_MODE_KEY)
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = NotificationPreferences()
            }
        }

        private fun parsePreferences(preferences: Preferences): NotificationPreferences {
            return NotificationPreferences(
                enabled = preferences[ENABLED_KEY] ?: true,
                presentationMode = parseMode(preferences[PRESENTATION_MODE_KEY]),
                digestMode = parseDigestMode(preferences[DIGEST_MODE_KEY]),
            )
        }

        private fun parseMode(rawValue: String?): NotificationPresentationMode {
            return NotificationPresentationMode.entries.firstOrNull { it.name == rawValue }
                ?: NotificationPresentationMode.Detailed
        }

        private fun parseDigestMode(rawValue: String?): NotificationDigestMode? {
            return NotificationDigestMode.entries.firstOrNull { it.name == rawValue }
        }

        private companion object {
            private val ENABLED_KEY = booleanPreferencesKey("notification_preferences_enabled")
            private val PRESENTATION_MODE_KEY = stringPreferencesKey("notification_preferences_presentation_mode")
            private val DIGEST_MODE_KEY = stringPreferencesKey("notification_preferences_digest_mode")
        }
    }
