package com.devpulse.app.data.repository

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import java.util.Locale

internal object SubscriptionsErrorMapper {
    private val emptySubscriptionsCodes = setOf("EMPTY_SUBSCRIPTIONS", "NO_SUBSCRIPTIONS")
    private val emptySubscriptionsMessageHints =
        setOf(
            "no subscriptions found",
            "no subscriptions yet",
            "subscriptions not found",
        )

    fun shouldTreatAsEmpty(error: ApiError): Boolean {
        if (error.kind != ApiErrorKind.BadRequest || error.statusCode != HTTP_BAD_REQUEST) {
            return false
        }

        val normalizedCode = error.code?.trim()?.uppercase(Locale.ROOT)
        if (normalizedCode != null && normalizedCode in emptySubscriptionsCodes) {
            return true
        }

        val normalizedMessage = error.userMessage.trim().lowercase(Locale.ROOT)
        return emptySubscriptionsMessageHints.any { hint ->
            normalizedMessage.contains(hint)
        }
    }

    private const val HTTP_BAD_REQUEST = 400
}
