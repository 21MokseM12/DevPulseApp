package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class DataStoreNotificationPreferencesStoreTest {
    @Test
    fun defaults_areSafeAndDetailed() {
        runTest {
            val store = createStore()

            val preferences = store.getPreferences()

            assertTrue(preferences.enabled)
            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
        }
    }

    @Test
    fun setEnabled_updatesStoredValue() {
        runTest {
            val store = createStore()
            store.setEnabled(false)

            assertFalse(store.getPreferences().enabled)
        }
    }

    @Test
    fun setPresentationMode_updatesStoredMode() {
        runTest {
            val store = createStore()
            store.setPresentationMode(NotificationPresentationMode.Compact)

            assertEquals(NotificationPresentationMode.Compact, store.getPreferences().presentationMode)
        }
    }

    @Test
    fun reset_returnsSafeDefaults() {
        runTest {
            val store = createStore()
            store.setEnabled(false)
            store.setPresentationMode(NotificationPresentationMode.Compact)

            store.reset()

            val preferences = store.getPreferences()
            assertTrue(preferences.enabled)
            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
        }
    }

    @Test
    fun invalidStoredMode_fallsBackToDetailed() {
        runTest {
            val tempFile = Files.createTempFile("notification-preferences-corrupted-store-test", ".preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile.toFile() })
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey("notification_preferences_presentation_mode")] = "UNKNOWN_MODE"
            }
            val store = DataStoreNotificationPreferencesStore(dataStore)

            val preferences = store.getPreferences()

            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
        }
    }

    @Test
    fun setDigestMode_updatesStoredMode() {
        runTest {
            val store = createStore()

            store.setDigestMode(NotificationDigestMode.Daily)

            assertEquals(NotificationDigestMode.Daily, store.getPreferences().digestMode)
        }
    }

    @Test
    fun readFailure_usesInMemoryFallbackAfterWriteFailure() {
        runTest {
            val failingDataStore = FailingPreferencesDataStore()
            val store = DataStoreNotificationPreferencesStore(failingDataStore)

            store.setEnabled(false)
            val preferences = store.getPreferences()

            assertFalse(preferences.enabled)
            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
        }
    }

    @Test
    fun readFailure_withoutOverride_returnsSafeDefaults() {
        runTest {
            val failingDataStore = FailingPreferencesDataStore()
            val store = DataStoreNotificationPreferencesStore(failingDataStore)

            val preferences = store.getPreferences()

            assertTrue(preferences.enabled)
            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
            assertEquals(null, preferences.digestMode)
        }
    }

    private fun createStore(): NotificationPreferencesStore {
        val file = Files.createTempFile("notification-preferences-store", ".preferences_pb").toFile()
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return DataStoreNotificationPreferencesStore(dataStore)
    }

    private class FailingPreferencesDataStore : DataStore<androidx.datastore.preferences.core.Preferences> {
        override val data: Flow<androidx.datastore.preferences.core.Preferences> =
            flow {
                throw IOException("read failed")
            }

        override suspend fun updateData(
            transform: suspend (
                t: androidx.datastore.preferences.core.Preferences,
            ) -> androidx.datastore.preferences.core.Preferences,
        ): androidx.datastore.preferences.core.Preferences {
            throw IOException("write failed")
        }
    }
}
