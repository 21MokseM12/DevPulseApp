package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.SubscriptionsSearchState
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import javax.inject.Inject

class ApplySubscriptionsSearchUseCase
    @Inject
    constructor() {
        operator fun invoke(
            links: List<TrackedLink>,
            state: SubscriptionsSearchState,
        ): List<TrackedLink> {
            val normalizedQuery = normalize(state.query)
            val terms = normalizedQuery.split(" ").filter { it.isNotBlank() }

            return links
                .filter { link ->
                    if (state.onlyTagged && link.tags.isEmpty()) {
                        return@filter false
                    }
                    if (state.hasFiltersOnly && link.filters.isEmpty()) {
                        return@filter false
                    }

                    val tagFilter = normalize(state.tagFilter.orEmpty())
                    if (tagFilter.isNotEmpty()) {
                        val hasMatchingTag = link.tags.any { normalize(it).contains(tagFilter) }
                        if (!hasMatchingTag) {
                            return@filter false
                        }
                    }

                    if (terms.isEmpty()) {
                        return@filter true
                    }

                    val normalizedUrl = normalize(link.url)
                    val normalizedTags = link.tags.map(::normalize)
                    val normalizedFilters = link.filters.map(::normalize)

                    terms.all { term ->
                        when {
                            term.startsWith(TAG_PREFIX) -> {
                                val value = term.removePrefix(TAG_PREFIX)
                                value.isNotEmpty() && normalizedTags.any { it.contains(value) }
                            }

                            term.startsWith(FILTER_PREFIX) -> {
                                val value = term.removePrefix(FILTER_PREFIX)
                                value.isNotEmpty() && normalizedFilters.any { it.contains(value) }
                            }

                            term.startsWith(URL_PREFIX) -> {
                                val value = term.removePrefix(URL_PREFIX)
                                value.isNotEmpty() && normalizedUrl.contains(value)
                            }

                            else -> {
                                normalizedUrl.contains(term) ||
                                    normalizedTags.any { it.contains(term) } ||
                                    normalizedFilters.any { it.contains(term) }
                            }
                        }
                    }
                }.let { filtered ->
                    when (state.sortMode) {
                        SubscriptionsSortMode.RECENTLY_ADDED -> {
                            filtered.sortedByDescending { it.id }
                        }

                        SubscriptionsSortMode.URL_ASCENDING -> {
                            filtered.sortedBy { normalize(it.url) }
                        }
                    }
                }
        }

        private fun normalize(value: String): String = value.trim().lowercase()

        private companion object {
            const val TAG_PREFIX = "tag:"
            const val FILTER_PREFIX = "filter:"
            const val URL_PREFIX = "url:"
        }
    }
