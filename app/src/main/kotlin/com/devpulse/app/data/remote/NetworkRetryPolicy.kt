package com.devpulse.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.min

internal data class NetworkTimeoutPolicy(
    val connectTimeoutSeconds: Long,
    val readTimeoutSeconds: Long,
    val writeTimeoutSeconds: Long,
    val callTimeoutSeconds: Long,
)

internal data class RetryBackoffPolicy(
    val maxRetries: Int,
    val baseDelayMs: Long,
    val maxDelayMs: Long,
)

internal object NetworkPolicyDefaults {
    val timeouts =
        NetworkTimeoutPolicy(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 20,
            writeTimeoutSeconds = 20,
            callTimeoutSeconds = 30,
        )

    val backoff =
        RetryBackoffPolicy(
            maxRetries = 2,
            baseDelayMs = 250L,
            maxDelayMs = 1_000L,
        )
}

internal class RetryPolicyInterceptor(
    private val backoffPolicy: RetryBackoffPolicy = NetworkPolicyDefaults.backoff,
    private val sleep: (Long) -> Unit = { delayMs -> Thread.sleep(delayMs) },
    private val telemetry: NetworkRetryTelemetry = NetworkRetryTelemetry.NONE,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastIOException: IOException? = null

        while (attempt <= backoffPolicy.maxRetries) {
            try {
                val response = chain.proceed(request)
                if (!shouldRetryResponse(request.method, response.code, attempt, backoffPolicy.maxRetries)) {
                    if (attempt > 0) {
                        telemetry.report(
                            RetryTelemetryEvent.Completed(
                                method = request.method,
                                totalAttempts = attempt,
                                result =
                                    if (response.isSuccessful) {
                                        RetryCompletionResult.SUCCESS
                                    } else {
                                        RetryCompletionResult.FAILURE
                                    },
                                finalStatusCode = response.code,
                                finalReason = if (response.isSuccessful) null else "HTTP ${response.code}",
                            ),
                        )
                    }
                    return response
                }
                response.close()
                attempt += 1
                val delayMs = computeBackoffDelayMs(attempt, backoffPolicy)
                telemetry.report(
                    RetryTelemetryEvent.Attempted(
                        method = request.method,
                        attempt = attempt,
                        delayMs = delayMs,
                        statusCode = response.code,
                    ),
                )
                sleep(delayMs)
            } catch (ioException: IOException) {
                if (!shouldRetryException(request.method, ioException, attempt, backoffPolicy.maxRetries)) {
                    if (attempt > 0) {
                        telemetry.report(
                            RetryTelemetryEvent.Completed(
                                method = request.method,
                                totalAttempts = attempt,
                                result = RetryCompletionResult.FAILURE,
                                finalReason = ioException::class.simpleName ?: "IOException",
                            ),
                        )
                    }
                    throw ioException
                }
                lastIOException = ioException
                attempt += 1
                val delayMs = computeBackoffDelayMs(attempt, backoffPolicy)
                telemetry.report(
                    RetryTelemetryEvent.Attempted(
                        method = request.method,
                        attempt = attempt,
                        delayMs = delayMs,
                        failureReason = ioException::class.simpleName ?: "IOException",
                    ),
                )
                sleep(delayMs)
            }
        }

        if (attempt > 0) {
            telemetry.report(
                RetryTelemetryEvent.Completed(
                    method = request.method,
                    totalAttempts = attempt,
                    result = RetryCompletionResult.FAILURE,
                    finalReason = lastIOException?.let { it::class.simpleName ?: "IOException" },
                ),
            )
        }
        throw lastIOException ?: IOException("Network request retry failed without captured exception.")
    }
}

internal fun shouldRetryResponse(
    method: String,
    code: Int,
    currentAttempt: Int,
    maxRetries: Int,
): Boolean {
    if (!isRetryableMethod(method)) return false
    if (currentAttempt >= maxRetries) return false
    return code in TRANSIENT_HTTP_CODES
}

internal fun shouldRetryException(
    method: String,
    exception: IOException,
    currentAttempt: Int,
    maxRetries: Int,
): Boolean {
    if (!isRetryableMethod(method)) return false
    if (currentAttempt >= maxRetries) return false
    return exception !is InterruptedIOException
}

internal fun isRetryableMethod(method: String): Boolean = method.equals("GET", ignoreCase = true)

internal fun computeBackoffDelayMs(
    nextAttempt: Int,
    backoffPolicy: RetryBackoffPolicy,
): Long {
    val factor = 1L shl (nextAttempt - 1).coerceAtLeast(0)
    return min(backoffPolicy.baseDelayMs * factor, backoffPolicy.maxDelayMs)
}

private val TRANSIENT_HTTP_CODES = setOf(408, 429, 500, 502, 503, 504)
