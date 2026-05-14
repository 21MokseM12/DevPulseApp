package com.devpulse.app.data.repository

import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.local.preferences.StoredSession
import com.devpulse.app.data.remote.ApiErrorMapper
import com.devpulse.app.data.remote.AuthTransportSecurityGuard
import com.devpulse.app.data.remote.ClientLoginHeaderInterceptor
import com.devpulse.app.data.remote.DefaultDevPulseRemoteDataSource
import com.devpulse.app.data.remote.DevPulseApi
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.repository.SubscriptionsResult
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
import java.util.concurrent.TimeUnit

class DefaultSubscriptionsRepositoryMockWebServerTest {
    private val moshi: Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Test
    fun getSubscriptions_mapsHappyPathAndAddsClientLoginHeader() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id": 5, "url": "https://example.org/news", "tags": ["news"], "filters": ["contains:kotlin"]}
                            ]
                            """.trimIndent(),
                        ),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.getSubscriptions()

                assertTrue(result is SubscriptionsResult.Success)
                val links = (result as SubscriptionsResult.Success).links
                assertEquals(1, links.size)
                assertEquals("https://example.org/news", links.first().url)
                assertEquals("moksem", server.takeRequest().getHeader("Client-Login"))
            }
        }

    @Test
    fun getSubscriptions_maps404ToNotFoundFailure() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody(
                            """
                            {
                              "description": "link not found",
                              "code": "404"
                            }
                            """.trimIndent(),
                        ),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.getSubscriptions()

                assertTrue(result is SubscriptionsResult.Failure)
                val failure = result as SubscriptionsResult.Failure
                assertEquals(ApiErrorKind.NotFound, failure.error.kind)
                assertEquals("link not found", failure.error.userMessage)
                assertEquals(404, failure.error.statusCode)
            }
        }

    @Test
    fun addSubscription_fallsBackToDefaultMessageWhenErrorBodyBroken() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody("broken-json"),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result =
                    repository.addSubscription(
                        link = "https://example.org/new",
                        tags = emptyList(),
                        filters = emptyList(),
                    )

                assertTrue(result is SubscriptionsResult.Failure)
                val failure = result as SubscriptionsResult.Failure
                assertEquals(ApiErrorKind.BadRequest, failure.error.kind)
                assertEquals("Проверьте корректность введенных данных.", failure.error.userMessage)
                assertEquals(400, failure.error.statusCode)
            }
        }

    @Test
    fun getSubscriptions_mapsSocketDelayToNetworkTimeoutFailure() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBodyDelay(1, TimeUnit.SECONDS)
                        .setBody("[]"),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                        timeoutMs = 200,
                    )

                val result = repository.getSubscriptions()

                assertTrue(result is SubscriptionsResult.Failure)
                val failure = result as SubscriptionsResult.Failure
                assertEquals(ApiErrorKind.NetworkTimeout, failure.error.kind)
            }
        }

    @Test
    fun removeSubscription_acceptsBotMessageResponseAndSendsClientLoginHeader() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"message":"removed"}"""),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.removeSubscription(link = "https://example.org/remove")

                assertTrue(result is SubscriptionsResult.Success)
                assertTrue((result as SubscriptionsResult.Success).links.isEmpty())
                assertEquals("moksem", server.takeRequest().getHeader("Client-Login"))
            }
        }

    private fun createRepository(
        server: MockWebServer,
        sessionStore: SessionStore,
        timeoutMs: Long = 1_000,
    ): DefaultSubscriptionsRepository {
        val client =
            OkHttpClient.Builder()
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
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
        return DefaultSubscriptionsRepository(remoteDataSource = remoteDataSource)
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
