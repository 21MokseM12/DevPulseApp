package com.devpulse.app.data.repository

import com.devpulse.app.data.local.db.CachedSubscriptionEntity
import com.devpulse.app.data.local.db.SubscriptionsCacheDao
import com.devpulse.app.data.local.db.SubscriptionsSyncStateEntity
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
import okhttp3.mockwebserver.SocketPolicy
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

                val result = repository.getSubscriptions(forceRefresh = true)

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

                val result = repository.getSubscriptions(forceRefresh = true)

                assertTrue(result is SubscriptionsResult.Failure)
                val failure = result as SubscriptionsResult.Failure
                assertEquals(ApiErrorKind.NotFound, failure.error.kind)
                assertEquals("link not found", failure.error.userMessage)
                assertEquals(404, failure.error.statusCode)
            }
        }

    @Test
    fun getSubscriptions_repro400_forEmptyAccount_mapsToEmptyStateSuccess() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {
                              "description": "No subscriptions found for this client",
                              "code": "EMPTY_SUBSCRIPTIONS"
                            }
                            """.trimIndent(),
                        ),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.getSubscriptions(forceRefresh = false)
                val request = server.takeRequest()

                assertEquals("GET", request.method)
                assertEquals("/api/v1/links", request.path)
                assertTrue(result is SubscriptionsResult.Success)
                val success = result as SubscriptionsResult.Success
                assertTrue(success.links.isEmpty())
                assertTrue(success.isStale.not())
                assertTrue(success.lastSyncAtEpochMs != null)
            }
        }

    @Test
    fun getSubscriptions_keepsReal400AsFailure_whenCodeIsNotEmptySubscriptions() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {
                              "description": "Validation failed",
                              "code": "VALIDATION_ERROR"
                            }
                            """.trimIndent(),
                        ),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.getSubscriptions(forceRefresh = true)

                assertTrue(result is SubscriptionsResult.Failure)
                val failure = result as SubscriptionsResult.Failure
                assertEquals(ApiErrorKind.BadRequest, failure.error.kind)
                assertEquals(400, failure.error.statusCode)
                assertEquals("Validation failed", failure.error.userMessage)
                assertEquals("VALIDATION_ERROR", failure.error.code)
            }
        }

    @Test
    fun getSubscriptions_maps400ToEmptyState_whenMessageContainsNoSubscriptionsHint() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {
                              "description": "No subscriptions yet for this client"
                            }
                            """.trimIndent(),
                        ),
                )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                    )

                val result = repository.getSubscriptions(forceRefresh = true)

                assertTrue(result is SubscriptionsResult.Success)
                val success = result as SubscriptionsResult.Success
                assertTrue(success.links.isEmpty())
                assertTrue(success.isStale.not())
                assertTrue(success.lastSyncAtEpochMs != null)
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

                val result = repository.getSubscriptions(forceRefresh = true)

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

    @Test
    fun getSubscriptions_returnsCachedDataWhenOfflineAfterSync() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"id": 8, "url": "https://example.org/cached", "tags": [], "filters": []}]"""),
                )
                server.enqueue(
                    MockResponse()
                        .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
                )
                val cacheDao = FakeSubscriptionsCacheDao()
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                        cacheDao = cacheDao,
                    )

                val first = repository.getSubscriptions(forceRefresh = true)
                val second = repository.getSubscriptions(forceRefresh = true)

                assertTrue(first is SubscriptionsResult.Success)
                assertTrue(second is SubscriptionsResult.Success)
                val staleSnapshot = second as SubscriptionsResult.Success
                assertTrue(staleSnapshot.isStale)
                assertEquals("https://example.org/cached", staleSnapshot.links.first().url)
                assertTrue(cacheDao.syncState?.isStale == true)
            }
        }

    @Test
    fun getSubscriptions_forceRefresh_replacesCacheSnapshot() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""[{"id": 2, "url": "https://example.org/new", "tags": [], "filters": []}]"""),
                )
                val cacheDao =
                    FakeSubscriptionsCacheDao(
                        cached =
                            mutableListOf(
                                CachedSubscriptionEntity(
                                    id = 1L,
                                    url = "https://example.org/old",
                                    tagsSerialized = "",
                                    filtersSerialized = "",
                                ),
                            ),
                    )
                val repository =
                    createRepository(
                        server = server,
                        sessionStore = FakeSessionStore(login = "moksem"),
                        cacheDao = cacheDao,
                    )

                val result = repository.getSubscriptions(forceRefresh = true)

                assertTrue(result is SubscriptionsResult.Success)
                assertEquals(1, cacheDao.cached.size)
                assertEquals("https://example.org/new", cacheDao.cached.first().url)
            }
        }

    private fun createRepository(
        server: MockWebServer,
        sessionStore: SessionStore,
        timeoutMs: Long = 1_000,
        cacheDao: SubscriptionsCacheDao = FakeSubscriptionsCacheDao(),
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
        return DefaultSubscriptionsRepository(
            remoteDataSource = remoteDataSource,
            subscriptionsCacheDao = cacheDao,
        )
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

    private class FakeSubscriptionsCacheDao(
        val cached: MutableList<CachedSubscriptionEntity> = mutableListOf(),
        var syncState: SubscriptionsSyncStateEntity? = null,
    ) : SubscriptionsCacheDao {
        override suspend fun getAll(): List<CachedSubscriptionEntity> = cached.toList()

        override suspend fun upsertAll(entities: List<CachedSubscriptionEntity>) {
            entities.forEach { entity ->
                cached.removeAll { it.id == entity.id }
                cached.add(entity)
            }
        }

        override suspend fun clearAll() {
            cached.clear()
        }

        override suspend fun replaceAll(entities: List<CachedSubscriptionEntity>) {
            cached.clear()
            cached.addAll(entities)
        }

        override suspend fun getSyncState(id: Int): SubscriptionsSyncStateEntity? = syncState

        override suspend fun upsertSyncState(entity: SubscriptionsSyncStateEntity) {
            syncState = entity
        }
    }
}
