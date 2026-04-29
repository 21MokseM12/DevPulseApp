package com.devpulse.app.data.remote

import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientLoginHeaderInterceptorTest {
    @Test
    fun addsClientLoginHeader_forLinksEndpoints() {
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                val sessionStore = FakeSessionStore()
                sessionStore.saveSession(login = "moksem")
                val client = buildClient(sessionStore)

                client.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/links"))
                        .get()
                        .build(),
                ).execute().close()

                val recorded = server.takeRequest()
                assertEquals("moksem", recorded.getHeader("Client-Login"))
            }
        }
    }

    @Test
    fun doesNotAddClientLoginHeader_forAuthEndpoints() {
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                val sessionStore = FakeSessionStore()
                sessionStore.saveSession(login = "moksem")
                val client = buildClient(sessionStore)

                client.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/clients"))
                        .post(ByteArray(0).toRequestBody(null))
                        .build(),
                ).execute().close()

                val recorded = server.takeRequest()
                assertNull(recorded.getHeader("Client-Login"))
            }
        }
    }

    @Test
    fun headerDisappearsAfterLogout() {
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                server.enqueue(MockResponse().setResponseCode(200))
                val sessionStore = FakeSessionStore()
                val client = buildClient(sessionStore)

                sessionStore.saveSession(login = "moksem")
                client.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/links"))
                        .get()
                        .build(),
                ).execute().close()

                sessionStore.clearSession()
                client.newCall(
                    Request.Builder()
                        .url(server.url("/api/v1/links"))
                        .get()
                        .build(),
                ).execute().close()

                val first = server.takeRequest()
                val second = server.takeRequest()
                assertEquals("moksem", first.getHeader("Client-Login"))
                assertNull(second.getHeader("Client-Login"))
            }
        }
    }

    private fun buildClient(sessionStore: SessionStore): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(ClientLoginHeaderInterceptor(sessionStore))
            .build()
    }

    private class FakeSessionStore : SessionStore {
        private val state = MutableStateFlow<StoredSession?>(null)

        override fun observeSession(): Flow<StoredSession?> = state

        override fun observeClientLogin(): Flow<String?> = state.map { session -> session?.login }

        override suspend fun getSession(): StoredSession? = state.value

        override suspend fun saveSession(
            login: String,
            isRegistered: Boolean,
        ) {
            state.value =
                StoredSession(
                    login = login,
                    isRegistered = isRegistered,
                    updatedAtEpochMs = 1_000L,
                )
        }

        override suspend fun clearSession() {
            state.value = null
        }
    }
}
