package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlin.coroutines.cancellation.CancellationException

class DevPulseRemoteDataSourceTest {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun getLinks_returnsSuccessForHttp200() = runTest {
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
    fun addLink_returnsApiFailureAndParsesErrorBody() = runTest {
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
            val result = dataSource.addLink(
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
    fun registerClient_propagatesCancellation() = runTest {
        val cancellableApi = object : DevPulseApi {
            override suspend fun registerClient(request: ClientCredentialsRequestDto) = throw CancellationException("cancelled")
            override suspend fun unregisterClient(request: ClientCredentialsRequestDto) = throw UnsupportedOperationException()
            override suspend fun getLinks() = throw UnsupportedOperationException()
            override suspend fun addLink(request: AddLinkRequestDto) = throw UnsupportedOperationException()
            override suspend fun removeLink(request: com.devpulse.app.data.remote.dto.RemoveLinkRequestDto) =
                throw UnsupportedOperationException()
        }
        val dataSource = DefaultDevPulseRemoteDataSource(
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

    private fun createDataSource(server: MockWebServer): DefaultDevPulseRemoteDataSource {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val api = retrofit.create(DevPulseApi::class.java)
        return DefaultDevPulseRemoteDataSource(api = api, moshi = moshi)
    }
}
