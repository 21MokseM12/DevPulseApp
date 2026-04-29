package com.devpulse.app.domain.repository

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.TrackedLink

sealed interface SubscriptionsResult {
    data class Success(
        val links: List<TrackedLink>,
    ) : SubscriptionsResult

    data class Failure(
        val error: ApiError,
    ) : SubscriptionsResult
}

interface SubscriptionsRepository {
    suspend fun getSubscriptions(): SubscriptionsResult
}
