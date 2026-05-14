package com.devpulse.app.domain.model

enum class UpdatesPeriodFilter {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

data class UpdatesFilterState(
    val query: String = "",
    val unreadOnly: Boolean = false,
    val source: String? = null,
    val period: UpdatesPeriodFilter = UpdatesPeriodFilter.ALL,
    val periodStartEpochMs: Long? = null,
    val periodEndEpochMs: Long? = null,
    val selectedTags: Set<String> = emptySet(),
) {
    val hasActiveFilters: Boolean
        get() =
            query.isNotBlank() ||
                unreadOnly ||
                source != null ||
                period != UpdatesPeriodFilter.ALL ||
                periodStartEpochMs != null ||
                periodEndEpochMs != null ||
                selectedTags.isNotEmpty()
}

enum class UpdatesQuickFilter {
    UNREAD,
    TODAY,
    GITHUB_ONLY,
}
