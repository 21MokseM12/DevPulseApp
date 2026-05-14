package com.devpulse.app.domain.model

enum class SubscriptionsSortMode {
    RECENTLY_ADDED,
    URL_ASCENDING,
}

data class SubscriptionsSearchState(
    val query: String = "",
    val tagFilter: String? = null,
    val hasFiltersOnly: Boolean = false,
    val onlyTagged: Boolean = false,
    val sortMode: SubscriptionsSortMode = SubscriptionsSortMode.RECENTLY_ADDED,
)
