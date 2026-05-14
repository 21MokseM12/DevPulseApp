package com.devpulse.app.domain.repository

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.TrackedLink

sealed interface SubscriptionsResult {
    data class Success(
        val links: List<TrackedLink>,
        val isStale: Boolean = false,
        val lastSyncAtEpochMs: Long? = null,
    ) : SubscriptionsResult

    data class Failure(
        val error: ApiError,
    ) : SubscriptionsResult
}

interface SubscriptionsRepository {
    suspend fun getSubscriptions(forceRefresh: Boolean = false): SubscriptionsResult

    suspend fun addSubscription(
        link: String,
        tags: List<String>,
        filters: List<String>,
    ): SubscriptionsResult

    suspend fun removeSubscription(link: String): SubscriptionsResult
}
