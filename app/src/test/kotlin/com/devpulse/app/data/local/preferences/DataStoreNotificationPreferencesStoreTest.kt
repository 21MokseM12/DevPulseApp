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
import java.time.DayOfWeek

class DataStoreNotificationPreferencesStoreTest {
    @Test
    fun defaults_areSafeAndDetailed() {
        runTest {
            val store = createStore()

            val preferences = store.getPreferences()

            assertTrue(preferences.enabled)
            assertEquals(NotificationPresentationMode.Detailed, preferences.presentationMode)
            assertEquals(false, preferences.quietHoursPolicy.enabled)
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
            assertEquals(false, preferences.quietHoursPolicy.enabled)
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

            store.setDigestMode(NotificationDigestMode.EverySixHours)

            assertEquals(NotificationDigestMode.EverySixHours, store.getPreferences().digestMode)
        }
    }

    @Test
    fun setDigestLastProcessedAt_updatesStoredTimestamp() {
        runTest {
            val store = createStore()

            store.setDigestLastProcessedAt(1_700_000_000_000L)

            assertEquals(1_700_000_000_000L, store.getPreferences().digestLastProcessedAtEpochMs)
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
            assertEquals(false, preferences.quietHoursPolicy.enabled)
        }
    }

    @Test
    fun setQuietHoursPolicy_persistsScheduleAndTimezone() {
        runTest {
            val store = createStore()
            val policy =
                QuietHoursPolicy(
                    enabled = true,
                    fromMinutes = 23 * 60,
                    toMinutes = 6 * 60 + 30,
                    weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    timezoneMode = QuietHoursTimezoneMode.Fixed,
                    fixedZoneId = "Europe/Moscow",
                )

            store.setQuietHoursPolicy(policy)
            val restored = store.getPreferences().quietHoursPolicy

            assertTrue(restored.enabled)
            assertEquals(23 * 60, restored.fromMinutes)
            assertEquals(6 * 60 + 30, restored.toMinutes)
            assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), restored.weekdays)
            assertEquals(QuietHoursTimezoneMode.Fixed, restored.timezoneMode)
            assertEquals("Europe/Moscow", restored.fixedZoneId)
        }
    }

    @Test
    fun setQuietHoursPolicy_invalidZone_fallsBackToSystemZone() {
        runTest {
            val store = createStore()
            store.setQuietHoursPolicy(
                QuietHoursPolicy(
                    enabled = true,
                    timezoneMode = QuietHoursTimezoneMode.Fixed,
                    fixedZoneId = "Mars/Colony",
                ),
            )

            val restored = store.getPreferences().quietHoursPolicy
            assertEquals(QuietHoursTimezoneMode.Fixed, restored.timezoneMode)
            assertTrue(restored.fixedZoneId != null)
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
