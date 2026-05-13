package com.devpulse.app.data.repository

import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.data.remote.ApiErrorMapper
import com.devpulse.app.data.remote.AuthTransportSecurityGuard
import com.devpulse.app.data.remote.ClientLoginHeaderInterceptor
import com.devpulse.app.data.remote.DefaultDevPulseRemoteDataSource
import com.devpulse.app.data.remote.DevPulseApi
import com.devpulse.app.domain.repository.MarkReadResult
import com.devpulse.app.domain.repository.NotificationsResult
import com.devpulse.app.domain.repository.UnreadCountResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DefaultNotificationsRepositoryMockWebServerTest {
    private val moshi: Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Test
    fun getNotifications_sendsQueryAndClientLoginHeader() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            {
                              "notifications": [
                                {
                                  "id": 9,
                                  "title": "Title",
                                  "content": "Body",
                                  "link": "https://example.org/x",
                                  "tags": ["android"],
                                  "isRead": false
                                }
                              ]
                            }
                            """.trimIndent(),
                        ),
                )
                val repository = createRepository(server = server, sessionStore = FakeSessionStore(login = "moksem"))

                val result = repository.getNotifications(limit = 30, offset = 10, tags = listOf("android"))

                assertTrue(result is NotificationsResult.Success)
                assertEquals(1, (result as NotificationsResult.Success).notifications.size)
                val request = server.takeRequest()
                assertEquals("/api/v1/notifications?limit=30&offset=10&tags=android", request.path)
                assertEquals("moksem", request.getHeader("Client-Login"))
            }
        }

    @Test
    fun getUnreadCount_parsesResponse() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"unreadCount":7}"""),
                )
                val repository = createRepository(server = server, sessionStore = FakeSessionStore(login = "moksem"))

                val result = repository.getUnreadCount()

                assertTrue(result is UnreadCountResult.Success)
                assertEquals(7, (result as UnreadCountResult.Success).unreadCount)
            }
        }

    @Test
    fun markRead_postsNotificationIdsAndReturnsMessage() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"message":"Marked"}"""),
                )
                val repository = createRepository(server = server, sessionStore = FakeSessionStore(login = "moksem"))

                val result = repository.markRead(notificationIds = listOf(1L, 2L))

                assertTrue(result is MarkReadResult.Success)
                assertEquals("Marked", (result as MarkReadResult.Success).message)
                val request = server.takeRequest()
                val body = request.body.readUtf8()
                assertTrue(body.contains(""""notificationIds":[1,2]"""))
                assertEquals("moksem", request.getHeader("Client-Login"))
            }
        }

    private fun createRepository(
        server: MockWebServer,
        sessionStore: SessionStore,
    ): DefaultNotificationsRepository {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(ClientLoginHeaderInterceptor(sessionStore))
                .build()
        val api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(DevPulseApi::class.java)
        val remoteDataSource =
            DefaultDevPulseRemoteDataSource(
                api = api,
                moshi = moshi,
                apiErrorMapper = ApiErrorMapper(),
                authTransportSecurityGuard = AllowAllAuthTransportSecurityGuard,
            )
        return DefaultNotificationsRepository(remoteDataSource = remoteDataSource)
    }

    private object AllowAllAuthTransportSecurityGuard : AuthTransportSecurityGuard {
        override fun getAuthTransportViolation() = null
    }

    private class FakeSessionStore(login: String?) : SessionStore {
        private val state =
            MutableStateFlow(
                login?.let {
                    StoredSession(
                        login = it,
                        isRegistered = true,
                        updatedAtEpochMs = 1_000L,
                    )
                },
            )

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
