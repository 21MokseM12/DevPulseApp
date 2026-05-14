package com.devpulse.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyInterceptorIntegrationTest {
    @Test
    fun getRequest_retriesOn503AndEventuallySucceeds() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val client = createClient()

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/links"))
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ok", response.body?.string())
            }

            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun postRequest_doesNotRetryOn503() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val client = createClient()

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/links"))
                    .post("body".toRequestBody())
                    .build(),
            ).execute().use { response ->
                assertEquals(503, response.code)
            }

            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun deleteRequest_doesNotRetryOn503() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val client = createClient()

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/links/42"))
                    .delete()
                    .build(),
            ).execute().use { response ->
                assertEquals(503, response.code)
            }

            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun getRequest_retriesOnIoFailureAndSucceeds() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))
            val client = createClient()

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/notifications"))
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
                assertTrue(response.body?.string()?.contains("recovered") == true)
            }

            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun getRequest_reportsRetryTelemetryForSuccessfulRecovery() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val telemetry = CapturingRetryTelemetry()
            val client = createClient(telemetry = telemetry)

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/links"))
                    .get()
                    .build(),
            ).execute().close()

            assertEquals(2, telemetry.events.size)
            assertEquals(
                RetryTelemetryEvent.Attempted(
                    method = "GET",
                    attempt = 1,
                    delayMs = 0,
                    statusCode = 503,
                ),
                telemetry.events.first(),
            )
            assertEquals(
                RetryTelemetryEvent.Completed(
                    method = "GET",
                    totalAttempts = 1,
                    result = RetryCompletionResult.SUCCESS,
                    finalStatusCode = 200,
                ),
                telemetry.events.last(),
            )
        }
    }

    @Test
    fun getRequest_reportsRetryTelemetryForExhaustedHttpRetries() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(503))
            val telemetry = CapturingRetryTelemetry()
            val client = createClient(telemetry = telemetry)

            client.newCall(
                Request.Builder()
                    .url(server.url("/api/v1/links"))
                    .get()
                    .build(),
            ).execute().use { response ->
                assertEquals(503, response.code)
            }

            assertEquals(3, telemetry.events.size)
            assertEquals(
                RetryTelemetryEvent.Completed(
                    method = "GET",
                    totalAttempts = 2,
                    result = RetryCompletionResult.FAILURE,
                    finalStatusCode = 503,
                    finalReason = "HTTP 503",
                ),
                telemetry.events.last(),
            )
        }
    }

    private fun createClient(telemetry: NetworkRetryTelemetry = NetworkRetryTelemetry.NONE): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                RetryPolicyInterceptor(
                    backoffPolicy =
                        RetryBackoffPolicy(
                            maxRetries = 2,
                            baseDelayMs = 0,
                            maxDelayMs = 0,
                        ),
                    telemetry = telemetry,
                ),
            )
            .build()
    }

    private class CapturingRetryTelemetry : NetworkRetryTelemetry {
        val events = mutableListOf<RetryTelemetryEvent>()

        override fun report(event: RetryTelemetryEvent) {
            events += event
        }
    }
}
