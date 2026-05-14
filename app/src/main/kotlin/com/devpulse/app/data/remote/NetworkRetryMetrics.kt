package com.devpulse.app.data.remote

internal enum class RetryCompletionResult {
    SUCCESS,
    FAILURE,
}

internal sealed interface RetryTelemetryEvent {
    data class Attempted(
        val method: String,
        val attempt: Int,
        val delayMs: Long,
        val statusCode: Int? = null,
        val failureReason: String? = null,
    ) : RetryTelemetryEvent

    data class Completed(
        val method: String,
        val totalAttempts: Int,
        val result: RetryCompletionResult,
        val finalStatusCode: Int? = null,
        val finalReason: String? = null,
    ) : RetryTelemetryEvent
}

internal fun interface NetworkRetryTelemetry {
    fun report(event: RetryTelemetryEvent)

    companion object {
        val NONE: NetworkRetryTelemetry = NetworkRetryTelemetry { _ -> }
    }
}
