package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
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
                assertEquals("invalid request", failure.error?.description)
                assertEquals("ValidationException", failure.error?.exceptionName)
            }
        }

    @Test
    fun registerClient_propagatesCancellation() =
        runTest {
            val cancellableApi =
                object : DevPulseApi {
                    override suspend fun registerClient(request: ClientCredentialsRequestDto) =
                        throw CancellationException("cancelled")

                    override suspend fun unregisterClient(request: ClientCredentialsRequestDto) =
                        throw UnsupportedOperationException()

                    override suspend fun getLinks() = throw UnsupportedOperationException()

                    override suspend fun addLink(request: AddLinkRequestDto) = throw UnsupportedOperationException()

                    override suspend fun removeLink(request: com.devpulse.app.data.remote.dto.RemoveLinkRequestDto) =
                        throw UnsupportedOperationException()
                }
            val dataSource =
                DefaultDevPulseRemoteDataSource(
                    api = cancellableApi,
                    moshi = moshi,
                )

            try {
                dataSource.registerClient(ClientCredentialsRequestDto(login = "moksem"))
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

            val result = dataSource.registerClient(ClientCredentialsRequestDto(login = "moksem"))

            assertTrue(result is RemoteCallResult.NetworkFailure)
            val failure = result as RemoteCallResult.NetworkFailure
            assertTrue(failure.throwable is IOException)
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
                val result = dataSource.unregisterClient(ClientCredentialsRequestDto(login = "moksem"))

                assertTrue(result is RemoteCallResult.ApiFailure)
                val failure = result as RemoteCallResult.ApiFailure
                assertEquals(500, failure.statusCode)
                assertEquals(null, failure.error)
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
        }

    private fun createDataSource(server: MockWebServer): DefaultDevPulseRemoteDataSource {
        val retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
        val api = retrofit.create(DevPulseApi::class.java)
        return DefaultDevPulseRemoteDataSource(api = api, moshi = moshi)
    }

    private fun createDataSource(api: DevPulseApi): DefaultDevPulseRemoteDataSource {
        return DefaultDevPulseRemoteDataSource(api = api, moshi = moshi)
    }

    private class FakeDevPulseApi(
        private val onRegisterClient: suspend (ClientCredentialsRequestDto) -> Response<Unit> =
            { throw UnsupportedOperationException() },
        private val onUnregisterClient: suspend (ClientCredentialsRequestDto) -> Response<Unit> =
            { throw UnsupportedOperationException() },
        private val onGetLinks: suspend () -> Response<List<LinkResponseDto>> =
            { throw UnsupportedOperationException() },
        private val onAddLink: suspend (AddLinkRequestDto) -> Response<LinkResponseDto> =
            { throw UnsupportedOperationException() },
        private val onRemoveLink: suspend (RemoveLinkRequestDto) -> Response<LinkResponseDto> =
            { throw UnsupportedOperationException() },
    ) : DevPulseApi {
        override suspend fun registerClient(request: ClientCredentialsRequestDto): Response<Unit> =
            onRegisterClient(request)

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): Response<Unit> =
            onUnregisterClient(request)

        override suspend fun getLinks(): Response<List<LinkResponseDto>> = onGetLinks()

        override suspend fun addLink(request: AddLinkRequestDto): Response<LinkResponseDto> = onAddLink(request)

        override suspend fun removeLink(request: RemoveLinkRequestDto): Response<LinkResponseDto> =
            onRemoveLink(request)
    }
}
