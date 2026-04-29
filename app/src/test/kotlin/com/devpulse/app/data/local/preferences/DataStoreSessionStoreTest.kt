package com.devpulse.app.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DataStoreSessionStoreTest {
    @Test
    fun saveSession_thenObserveAndGetSessionReturnStoredValues() {
        runTest {
            val sessionStore = createSessionStore()

            sessionStore.saveSession(login = "moksem", isRegistered = true)
            val observed = sessionStore.observeSession().first()
            val direct = sessionStore.getSession()

            requireNotNull(observed)
            requireNotNull(direct)
            assertEquals("moksem", observed.login)
            assertEquals("moksem", direct.login)
            assertTrue(observed.isRegistered)
            assertTrue(direct.updatedAtEpochMs > 0L)
        }
    }

    @Test
    fun clearSession_removesAllData() {
        runTest {
            val sessionStore = createSessionStore()
            sessionStore.saveSession(login = "moksem")

            sessionStore.clearSession()

            assertNull(sessionStore.getSession())
            assertNull(sessionStore.observeClientLogin().first())
        }
    }

    @Test
    fun saveSession_canStoreServiceFlags() {
        runTest {
            val sessionStore = createSessionStore()

            sessionStore.saveSession(login = "moksem", isRegistered = false)
            val session = requireNotNull(sessionStore.getSession())

            assertFalse(session.isRegistered)
        }
    }

    private fun createSessionStore(): SessionStore {
        val tempFile = Files.createTempFile("session-store-test", ".preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                produceFile = { tempFile.toFile() },
            )
        return DataStoreSessionStore(dataStore)
    }
}
