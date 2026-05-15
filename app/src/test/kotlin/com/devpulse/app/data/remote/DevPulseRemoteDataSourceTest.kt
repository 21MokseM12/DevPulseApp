package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.devpulse.app.domain.model.ApiErrorKind
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class DevPulseRemoteDataSourceTest {
    private val apiErrorMapper = ApiErrorMapper()
    private val moshi: Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Test
    fun getLinks_returnsSuccessForHttp200() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id": 1, "url": "https://example.org", "tags": ["news"], "filters": ["contains:kotlin"]}
                            ]
                            """.trimIndent(),
                        ),
                )

                val dataSource = createDataSource(server)
                val result = dataSource.getLinks()

                assertTrue(result is RemoteCallResult.Success)
                val payload = (result as RemoteCallResult.Success).data
                assertEquals(1, payload.size)
                assertEquals("https://example.org", payload.first().url)
                assertEquals(200, result.statusCode)
            }
        }

    @Test
    fun registerClient_sendsLoginAndPasswordInRequestBody() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                val dataSource = createDataSource(server)

                val result =
                    dataSource.registerClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.Success)
                val request = server.takeRequest()
                val requestBody = request.body.readUtf8()
                assertTrue(requestBody.contains(""""login":"moksem""""))
                assertTrue(requestBody.contains(""""password":"secret""""))
            }
        }

    @Test
    fun addLink_returnsApiFailureAndParsesErrorBody() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {
                              "description": "invalid request",
                              "code": "400",
                              "exceptionName": "ValidationException",
                              "exceptionMessage": "link is malformed",
                              "stacktrace": ["line1"]
                            }
                            """.trimIndent(),
                        ),
                )

                val dataSource = createDataSource(server)
                val result =
                    dataSource.addLink(
                        AddLinkRequestDto(
                            link = "bad-link",
                            tags = emptyList(),
                            filters = emptyList(),
                        ),
                    )

                assertTrue(result is RemoteCallResult.ApiFailure)
                val failure = result as RemoteCallResult.ApiFailure
                assertEquals(400, failure.statusCode)
                assertEquals(ApiErrorKind.BadRequest, failure.error.kind)
                assertEquals("invalid request", failure.error.userMessage)
                assertEquals("400", failure.error.code)
            }
        }

    @Test
    fun registerClient_propagatesCancellation() =
        runTest {
            val cancellableApi =
                object : DevPulseApi {
                    override suspend fun loginClient(request: ClientCredentialsRequestDto) =
                        throw UnsupportedOperationException()

                    override suspend fun registerClient(request: ClientCredentialsRequestDto) =
                        throw CancellationException("cancelled")

                    override suspend fun unregisterClient(request: ClientCredentialsRequestDto) =
                        throw UnsupportedOperationException()

                    override suspend fun getLinks() = throw UnsupportedOperationException()

                    override suspend fun addLink(request: AddLinkRequestDto) = throw UnsupportedOperationException()

                    override suspend fun removeLink(request: com.devpulse.app.data.remote.dto.RemoveLinkRequestDto) =
                        throw UnsupportedOperationException()

                    override suspend fun getNotifications(
                        limit: Int,
                        offset: Int,
                        tags: List<String>,
                    ) = throw UnsupportedOperationException()

                    override suspend fun getUnreadNotificationsCount() = throw UnsupportedOperationException()

                    override suspend fun markNotificationsRead(request: MarkReadRequestDto) =
                        throw UnsupportedOperationException()
                }
            val dataSource =
                DefaultDevPulseRemoteDataSource(
                    api = cancellableApi,
                    moshi = moshi,
                    apiErrorMapper = apiErrorMapper,
                    authTransportSecurityGuard = AllowAllAuthTransportSecurityGuard,
                )

            try {
                dataSource.registerClient(
                    ClientCredentialsRequestDto(
                        login = "moksem",
                        password = "secret",
                    ),
                )
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
                // expected
            }
        }

    @Test
    fun registerClient_returnsNetworkFailureForIoException() =
        runTest {
            val dataSource =
                createDataSource(
                    api =
                        FakeDevPulseApi(
                            onRegisterClient = { throw IOException("timeout") },
                        ),
                )

            val result =
                dataSource.registerClient(
                    ClientCredentialsRequestDto(
                        login = "moksem",
                        password = "secret",
                    ),
                )

            assertTrue(result is RemoteCallResult.NetworkFailure)
            val failure = result as RemoteCallResult.NetworkFailure
            assertTrue(failure.throwable is IOException)
            assertEquals(ApiErrorKind.Network, failure.error.kind)
        }

    @Test
    fun unregisterClient_returnsApiFailureWithNullErrorForInvalidBody() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(500)
                        .setBody("not-a-json"),
                )

                val dataSource = createDataSource(server)
                val result =
                    dataSource.unregisterClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.ApiFailure)
                val failure = result as RemoteCallResult.ApiFailure
                assertEquals(500, failure.statusCode)
                assertEquals(ApiErrorKind.Unknown, failure.error.kind)
                assertEquals("Произошла ошибка сервера. Попробуйте позже.", failure.error.userMessage)
            }
        }

    @Test
    fun removeLink_returnsMessageResponseForHttp200() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"message":"Link removed"}"""),
                )

                val dataSource = createDataSource(server)
                val result = dataSource.removeLink(RemoveLinkRequestDto(link = "https://example.org"))

                assertTrue(result is RemoteCallResult.Success)
                assertEquals("Link removed", (result as RemoteCallResult.Success).data.message)
            }
        }

    @Test
    fun getNotifications_addsExpectedQueryParameters() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"notifications":[],"limit":50,"offset":20}"""),
                )

                val dataSource = createDataSource(server)
                val result = dataSource.getNotifications(limit = 50, offset = 20, tags = listOf("kotlin", "android"))

                assertTrue(result is RemoteCallResult.Success)
                val request = server.takeRequest()
                assertEquals("/api/v1/notifications?limit=50&offset=20&tags=kotlin&tags=android", request.path)
            }
        }

    @Test
    fun markNotificationsRead_postsIdsAndParsesUpdatedCount() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"updatedCount":2}"""),
                )

                val dataSource = createDataSource(server)
                val result =
                    dataSource.markNotificationsRead(
                        MarkReadRequestDto(ids = listOf(3L, 4L)),
                    )

                assertTrue(result is RemoteCallResult.Success)
                assertEquals(2, (result as RemoteCallResult.Success).data.updatedCount)
                val request = server.takeRequest()
                assertEquals("""{"ids":[3,4]}""", request.body.readUtf8())
            }
        }

    @Test
    fun getLinks_returnsNetworkFailureWhenBodyIsNull() =
        runTest {
            @Suppress("UNCHECKED_CAST")
            val nullBodyResponse =
                Response.success<List<LinkResponseDto>?>(null) as Response<List<LinkResponseDto>>
            val dataSource =
                createDataSource(
                    api =
                        FakeDevPulseApi(
                            onGetLinks = { nullBodyResponse },
                        ),
                )

            val result = dataSource.getLinks()

            assertTrue(result is RemoteCallResult.NetworkFailure)
            val failure = result as RemoteCallResult.NetworkFailure
            assertTrue(failure.throwable is IllegalStateException)
            assertEquals(ApiErrorKind.Unknown, failure.error.kind)
        }

    @Test
    fun getLinks_returnsNetworkFailureForUnexpectedException() =
        runTest {
            val dataSource =
                createDataSource(
                    api =
                        FakeDevPulseApi(
                            onGetLinks = { throw IllegalArgumentException("boom") },
                        ),
                )

            val result = dataSource.getLinks()

            assertTrue(result is RemoteCallResult.NetworkFailure)
            val failure = result as RemoteCallResult.NetworkFailure
            assertTrue(failure.throwable is IllegalArgumentException)
            assertEquals(ApiErrorKind.Unknown, failure.error.kind)
        }

    @Test
    fun registerClient_returnsConfigurationFailureWhenTransportIsInsecure() =
        runTest {
            val guard =
                object : AuthTransportSecurityGuard {
                    override fun getAuthTransportViolation() =
                        com.devpulse.app.domain.model.ApiError(
                            kind = ApiErrorKind.Configuration,
                            userMessage = "Авторизация недоступна: небезопасный адрес сервера.",
                        )
                }
            val dataSource =
                DefaultDevPulseRemoteDataSource(
                    api = FakeDevPulseApi(),
                    moshi = moshi,
                    apiErrorMapper = apiErrorMapper,
                    authTransportSecurityGuard = guard,
                )

            val result =
                dataSource.registerClient(
                    ClientCredentialsRequestDto(
                        login = "moksem",
                        password = "secret",
                    ),
                )

            assertTrue(result is RemoteCallResult.NetworkFailure)
            val failure = result as RemoteCallResult.NetworkFailure
            assertEquals(ApiErrorKind.Configuration, failure.error.kind)
        }

    @Test
    fun loginClient_usesDedicatedLoginEndpointWhenAvailable() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))

                val dataSource = createDataSource(server)
                val result =
                    dataSource.loginClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.Success)
                assertEquals(1, server.requestCount)
                assertEquals("/api/v1/clients/login", server.takeRequest().path)
            }
        }

    @Test
    fun loginClient_fallsBackToRegisterWhenLoginEndpointIsMissing() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody(
                            """
                            {"description":"Endpoint not found","code":"not_found"}
                            """.trimIndent(),
                        ),
                )
                server.enqueue(MockResponse().setResponseCode(200))

                val dataSource = createDataSource(server)
                val result =
                    dataSource.loginClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.Success)
                assertEquals(2, server.requestCount)
                assertEquals("/api/v1/clients/login", server.takeRequest().path)
                assertEquals("/api/v1/clients", server.takeRequest().path)
            }
        }

    @Test
    fun loginClient_fallbackTreatsAlreadyExistsAsSuccessfulLogin() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(405)
                        .setBody(
                            """
                            {"description":"Method not allowed","code":"method_not_allowed"}
                            """.trimIndent(),
                        ),
                )
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {"description":"Client already exists","code":"already_exists"}
                            """.trimIndent(),
                        ),
                )

                val dataSource = createDataSource(server)
                val result =
                    dataSource.loginClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.Success)
                assertEquals(2, server.requestCount)
            }
        }

    @Test
    fun loginClient_fallbackCanReturnBadRequest_whenRegisterConflictIsNotRecognized() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(404)
                        .setBody(
                            """
                            {"description":"Endpoint not found","code":"not_found"}
                            """.trimIndent(),
                        ),
                )
                server.enqueue(
                    MockResponse()
                        .setResponseCode(400)
                        .setBody(
                            """
                            {"description":"Client cannot be created in current state","code":"register_rejected"}
                            """.trimIndent(),
                        ),
                )

                val dataSource = createDataSource(server)
                val result =
                    dataSource.loginClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.ApiFailure)
                val failure = result as RemoteCallResult.ApiFailure
                assertEquals(400, failure.statusCode)
                assertEquals(ApiErrorKind.BadRequest, failure.error.kind)
                assertEquals("Client cannot be created in current state", failure.error.userMessage)
                assertEquals(2, server.requestCount)
                assertEquals("/api/v1/clients/login", server.takeRequest().path)
                assertEquals("/api/v1/clients", server.takeRequest().path)
            }
        }

    @Test
    fun loginClient_returnsOriginalApiFailureForMalformedBadRequest() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(400).setBody("not-a-json"))

                val dataSource = createDataSource(server)
                val result =
                    dataSource.loginClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.ApiFailure)
                val failure = result as RemoteCallResult.ApiFailure
                assertEquals(ApiErrorKind.BadRequest, failure.error.kind)
                assertEquals(400, failure.statusCode)
                assertEquals(1, server.requestCount)
                assertEquals("/api/v1/clients/login", server.takeRequest().path)
            }
        }

    @Test
    fun registerClient_withTransportViolation_doesNotSendHttpRequest() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                val violatingGuard =
                    object : AuthTransportSecurityGuard {
                        override fun getAuthTransportViolation() =
                            com.devpulse.app.domain.model.ApiError(
                                kind = ApiErrorKind.Configuration,
                                userMessage = "blocked",
                            )
                    }
                val retrofit =
                    Retrofit.Builder()
                        .baseUrl(server.url("/"))
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                val api = retrofit.create(DevPulseApi::class.java)
                val dataSource =
                    DefaultDevPulseRemoteDataSource(
                        api = api,
                        moshi = moshi,
                        apiErrorMapper = apiErrorMapper,
                        authTransportSecurityGuard = violatingGuard,
                    )

                val result =
                    dataSource.registerClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.NetworkFailure)
                assertEquals(0, server.requestCount)
            }
        }

    @Test
    fun unregisterClient_withTransportViolation_doesNotSendHttpRequest() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200))
                val violatingGuard =
                    object : AuthTransportSecurityGuard {
                        override fun getAuthTransportViolation() =
                            com.devpulse.app.domain.model.ApiError(
                                kind = ApiErrorKind.Configuration,
                                userMessage = "blocked",
                            )
                    }
                val retrofit =
                    Retrofit.Builder()
                        .baseUrl(server.url("/"))
                        .addConverterFactory(MoshiConverterFactory.create(moshi))
                        .build()
                val api = retrofit.create(DevPulseApi::class.java)
                val dataSource =
                    DefaultDevPulseRemoteDataSource(
                        api = api,
                        moshi = moshi,
                        apiErrorMapper = apiErrorMapper,
                        authTransportSecurityGuard = violatingGuard,
                    )

                val result =
                    dataSource.unregisterClient(
                        ClientCredentialsRequestDto(
                            login = "moksem",
                            password = "secret",
                        ),
                    )

                assertTrue(result is RemoteCallResult.NetworkFailure)
                assertEquals(0, server.requestCount)
            }
        }

    private fun createDataSource(server: MockWebServer): DefaultDevPulseRemoteDataSource {
        val retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        val api = retrofit.create(DevPulseApi::class.java)
        return DefaultDevPulseRemoteDataSource(
            api = api,
            moshi = moshi,
            apiErrorMapper = apiErrorMapper,
            authTransportSecurityGuard = AllowAllAuthTransportSecurityGuard,
        )
    }

    private fun createDataSource(api: DevPulseApi): DefaultDevPulseRemoteDataSource {
        return DefaultDevPulseRemoteDataSource(
            api = api,
            moshi = moshi,
            apiErrorMapper = apiErrorMapper,
            authTransportSecurityGuard = AllowAllAuthTransportSecurityGuard,
        )
    }

    private class FakeDevPulseApi(
        private val onLoginClient: suspend (ClientCredentialsRequestDto) -> Response<Unit> =
            { throw UnsupportedOperationException() },
        private val onRegisterClient: suspend (ClientCredentialsRequestDto) -> Response<Unit> =
            { throw UnsupportedOperationException() },
        private val onUnregisterClient: suspend (ClientCredentialsRequestDto) -> Response<Unit> =
            { throw UnsupportedOperationException() },
        private val onGetLinks: suspend () -> Response<List<LinkResponseDto>> =
            { throw UnsupportedOperationException() },
        private val onAddLink: suspend (AddLinkRequestDto) -> Response<LinkResponseDto> =
            { throw UnsupportedOperationException() },
        private val onRemoveLink: suspend (RemoveLinkRequestDto) -> Response<BotApiMessageResponseDto> =
            { throw UnsupportedOperationException() },
        private val onGetNotifications: suspend (Int, Int, List<String>) -> Response<NotificationListResponseDto> =
            { _, _, _ -> throw UnsupportedOperationException() },
        private val onGetUnreadCount: suspend () -> Response<UnreadCountResponseDto> =
            { throw UnsupportedOperationException() },
        private val onMarkNotificationsRead: suspend (MarkReadRequestDto) -> Response<MarkReadResponseDto> =
            { throw UnsupportedOperationException() },
    ) : DevPulseApi {
        override suspend fun loginClient(request: ClientCredentialsRequestDto): Response<Unit> = onLoginClient(request)

        override suspend fun registerClient(request: ClientCredentialsRequestDto): Response<Unit> =
            onRegisterClient(request)

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): Response<Unit> =
            onUnregisterClient(request)

        override suspend fun getLinks(): Response<List<LinkResponseDto>> = onGetLinks()

        override suspend fun addLink(request: AddLinkRequestDto): Response<LinkResponseDto> = onAddLink(request)

        override suspend fun removeLink(request: RemoveLinkRequestDto): Response<BotApiMessageResponseDto> =
            onRemoveLink(request)

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): Response<NotificationListResponseDto> = onGetNotifications(limit, offset, tags)

        override suspend fun getUnreadNotificationsCount(): Response<UnreadCountResponseDto> = onGetUnreadCount()

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): Response<MarkReadResponseDto> =
            onMarkNotificationsRead(request)
    }

    private object AllowAllAuthTransportSecurityGuard : AuthTransportSecurityGuard {
        override fun getAuthTransportViolation() = null
    }
}
