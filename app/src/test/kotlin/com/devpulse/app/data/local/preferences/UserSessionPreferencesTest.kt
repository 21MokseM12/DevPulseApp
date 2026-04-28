package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSessionPreferencesTest {
    @Test
    fun clientLogin_returnsNullByDefault() =
        runTest {
            val preferences = UserSessionPreferences(FakePreferencesDataStore())

            assertNull(preferences.clientLogin.first())
        }

    @Test
    fun saveClientLogin_persistsValueInFlow() =
        runTest {
            val preferences = UserSessionPreferences(FakePreferencesDataStore())

            preferences.saveClientLogin("moksem")

            assertEquals("moksem", preferences.clientLogin.first())
        }

    @Test
    fun saveClientLogin_overwritesPreviouslySavedValue() =
        runTest {
            val preferences = UserSessionPreferences(FakePreferencesDataStore())
            preferences.saveClientLogin("old")

            preferences.saveClientLogin("new")

            assertEquals("new", preferences.clientLogin.first())
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
