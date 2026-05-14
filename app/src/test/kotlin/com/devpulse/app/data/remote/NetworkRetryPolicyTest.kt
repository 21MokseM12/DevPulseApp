package com.devpulse.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

class NetworkRetryPolicyTest {
    @Test
    fun shouldRetryResponse_retriesGetOnTransientHttpCode() {
        val shouldRetry =
            shouldRetryResponse(
                method = "GET",
                code = 503,
                currentAttempt = 0,
                maxRetries = 2,
            )

        assertTrue(shouldRetry)
    }

    @Test
    fun shouldRetryResponse_doesNotRetryPostOnTransientHttpCode() {
        val shouldRetry =
            shouldRetryResponse(
                method = "POST",
                code = 503,
                currentAttempt = 0,
                maxRetries = 2,
            )

        assertFalse(shouldRetry)
    }

    @Test
    fun shouldRetryResponse_doesNotRetryDeleteOnTransientHttpCode() {
        val shouldRetry =
            shouldRetryResponse(
                method = "DELETE",
                code = 503,
                currentAttempt = 0,
                maxRetries = 2,
            )

        assertFalse(shouldRetry)
    }

    @Test
    fun shouldRetryException_doesNotRetryInterruptedIoCancellation() {
        val shouldRetry =
            shouldRetryException(
                method = "GET",
                exception = InterruptedIOException("canceled"),
                currentAttempt = 0,
                maxRetries = 2,
            )

        assertFalse(shouldRetry)
    }

    @Test
    fun shouldRetryException_retriesGetForRegularIOException() {
        val shouldRetry =
            shouldRetryException(
                method = "GET",
                exception = IOException("network down"),
                currentAttempt = 0,
                maxRetries = 2,
            )

        assertTrue(shouldRetry)
    }

    @Test
    fun computeBackoffDelayMs_appliesExponentialBackoffWithCap() {
        val backoffPolicy =
            RetryBackoffPolicy(
                maxRetries = 3,
                baseDelayMs = 200,
                maxDelayMs = 500,
            )

        assertEquals(200, computeBackoffDelayMs(nextAttempt = 1, backoffPolicy = backoffPolicy))
        assertEquals(400, computeBackoffDelayMs(nextAttempt = 2, backoffPolicy = backoffPolicy))
        assertEquals(500, computeBackoffDelayMs(nextAttempt = 3, backoffPolicy = backoffPolicy))
    }
}
