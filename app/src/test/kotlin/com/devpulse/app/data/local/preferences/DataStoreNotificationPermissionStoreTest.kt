package com.devpulse.app.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DataStoreNotificationPermissionStoreTest {
    @Test
    fun hasRequested_initiallyFalse() {
        runTest {
            val store = createStore()

            assertFalse(store.hasRequested())
            assertFalse(store.observeHasRequested().first())
        }
    }

    @Test
    fun markRequested_setsRequestedFlag() {
        runTest {
            val store = createStore()

            store.markRequested()

            assertTrue(store.hasRequested())
            assertTrue(store.observeHasRequested().first())
        }
    }

    @Test
    fun clearRequestedFlag_resetsRequestedState() {
        runTest {
            val store = createStore()
            store.markRequested()

            store.clearRequestedFlag()

            assertFalse(store.hasRequested())
            assertFalse(store.observeHasRequested().first())
        }
    }

    private fun createStore(): NotificationPermissionStore {
        val tempFile = Files.createTempFile("notification-permission-store-test", ".preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFile.toFile() },
            )
        return DataStoreNotificationPermissionStore(dataStore)
    }
}
