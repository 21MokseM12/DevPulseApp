package com.devpulse.app.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class DataStorePushTokenStoreTest {
    @Test
    fun saveToken_thenObserveAndGetTokenReturnStoredValue() {
        runTest {
            val pushTokenStore = createStore()

            pushTokenStore.saveToken("token-123")

            assertEquals("token-123", pushTokenStore.observeToken().first())
            assertEquals("token-123", pushTokenStore.getToken())
        }
    }

    @Test
    fun clearToken_removesStoredValue() {
        runTest {
            val pushTokenStore = createStore()
            pushTokenStore.saveToken("token-123")

            pushTokenStore.clearToken()

            assertNull(pushTokenStore.observeToken().first())
            assertNull(pushTokenStore.getToken())
        }
    }

    private fun createStore(): PushTokenStore {
        val tempFile = Files.createTempFile("push-token-store-test", ".preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFile.toFile() },
            )
        return DataStorePushTokenStore(dataStore)
    }
}
