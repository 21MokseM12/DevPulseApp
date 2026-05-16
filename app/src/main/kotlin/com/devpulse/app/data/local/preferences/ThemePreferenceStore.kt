package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

interface ThemePreferenceStore {
    fun observeThemeMode(): Flow<AppThemeMode>

    suspend fun setThemeMode(mode: AppThemeMode)
}

@Singleton
class DataStoreThemePreferenceStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ThemePreferenceStore {
        override fun observeThemeMode(): Flow<AppThemeMode> {
            return dataStore.data.map { preferences ->
                parseThemeMode(preferences[THEME_MODE_KEY])
            }
        }

        override suspend fun setThemeMode(mode: AppThemeMode) {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.name
            }
        }

        private fun parseThemeMode(rawValue: String?): AppThemeMode {
            return AppThemeMode.entries.firstOrNull { it.name == rawValue } ?: AppThemeMode.SYSTEM
        }

        private companion object {
            private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        }
    }
