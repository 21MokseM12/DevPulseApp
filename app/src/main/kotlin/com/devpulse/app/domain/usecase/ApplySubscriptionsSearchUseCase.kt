package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.SubscriptionsSearchState
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import javax.inject.Inject

class ApplySubscriptionsSearchUseCase
    @Inject
    constructor() {
        private var cachedLinksReference: List<TrackedLink>? = null
        private var cachedSearchIndex: SearchIndex? = null

        operator fun invoke(
            links: List<TrackedLink>,
            state: SubscriptionsSearchState,
        ): List<TrackedLink> {
            val searchIndex = getOrBuildSearchIndex(links)
            val normalizedQuery = state.normalizedQuery()
            val terms = normalizedQuery.split(" ").filter { it.isNotBlank() }
            val normalizedTagFilter = normalize(state.tagFilter.orEmpty())
            val candidateIndexes =
                collectCandidateIndexes(
                    searchIndex = searchIndex,
                    state = state,
                    terms = terms,
                    normalizedTagFilter = normalizedTagFilter,
                )

            val filtered =
                candidateIndexes
                    .asSequence()
                    .map { index -> searchIndex.entries[index] }
                    .filter { entry ->
                        matchesEntry(
                            entry = entry,
                            state = state,
                            terms = terms,
                            normalizedTagFilter = normalizedTagFilter,
                        )
                    }.map { entry -> entry.link }
                    .toList()

            return when (state.sortMode) {
                SubscriptionsSortMode.RECENTLY_ADDED -> {
                    filtered.sortedByDescending { it.id }
                }

                SubscriptionsSortMode.URL_ASCENDING -> {
                    filtered.sortedBy { normalize(it.url) }
                }
            }
        }

        private fun getOrBuildSearchIndex(links: List<TrackedLink>): SearchIndex {
            if (cachedLinksReference === links && cachedSearchIndex != null) {
                return requireNotNull(cachedSearchIndex)
            }
            val built = buildSearchIndex(links)
            cachedLinksReference = links
            cachedSearchIndex = built
            return built
        }

        private fun buildSearchIndex(links: List<TrackedLink>): SearchIndex {
            val entries = ArrayList<IndexedLink>(links.size)
            val allTrigrams = linkedMapOf<String, MutableList<Int>>()
            val urlTrigrams = linkedMapOf<String, MutableList<Int>>()
            val tagTrigrams = linkedMapOf<String, MutableList<Int>>()
            val filterTrigrams = linkedMapOf<String, MutableList<Int>>()
            val taggedIndexes = ArrayList<Int>(links.size)
            val filteredIndexes = ArrayList<Int>(links.size)
            val allIndexes = IntArray(links.size) { index -> index }

            links.forEachIndexed { index, link ->
                val normalizedUrl = normalize(link.url)
                val normalizedTags = link.tags.map(::normalize)
                val normalizedFilters = link.filters.map(::normalize)
                val hasTags = normalizedTags.isNotEmpty()
                val hasFilters = normalizedFilters.isNotEmpty()
                if (hasTags) {
                    taggedIndexes += index
                }
                if (hasFilters) {
                    filteredIndexes += index
                }
                entries +=
                    IndexedLink(
                        link = link,
                        normalizedUrl = normalizedUrl,
                        normalizedTags = normalizedTags,
                        normalizedFilters = normalizedFilters,
                        hasTags = hasTags,
                        hasFilters = hasFilters,
                    )
                indexTrigrams(
                    text =
                        buildString {
                            append(normalizedUrl)
                            normalizedTags.forEach { tag ->
                                append(' ')
                                append(tag)
                            }
                            normalizedFilters.forEach { filter ->
                                append(' ')
                                append(filter)
                            }
                        },
                    linkIndex = index,
                    target = allTrigrams,
                )
                indexTrigrams(
                    text = normalizedUrl,
                    linkIndex = index,
                    target = urlTrigrams,
                )
                normalizedTags.forEach { tag ->
                    indexTrigrams(text = tag, linkIndex = index, target = tagTrigrams)
                }
                normalizedFilters.forEach { filter ->
                    indexTrigrams(text = filter, linkIndex = index, target = filterTrigrams)
                }
            }

            return SearchIndex(
                entries = entries,
                allIndexes = allIndexes,
                taggedIndexes = taggedIndexes.toIntArray(),
                filteredIndexes = filteredIndexes.toIntArray(),
                allTrigrams = freezeIndex(allTrigrams),
                urlTrigrams = freezeIndex(urlTrigrams),
                tagTrigrams = freezeIndex(tagTrigrams),
                filterTrigrams = freezeIndex(filterTrigrams),
            )
        }

        private fun freezeIndex(index: Map<String, MutableList<Int>>): Map<String, IntArray> {
            return index.mapValues { (_, value) -> value.toIntArray() }
        }

        private fun indexTrigrams(
            text: String,
            linkIndex: Int,
            target: MutableMap<String, MutableList<Int>>,
        ) {
            val normalized = normalize(text)
            if (normalized.length < TRIGRAM_LENGTH) return
            val trigrams = extractTrigrams(normalized)
            trigrams.forEach { trigram ->
                val bucket = target.getOrPut(trigram) { mutableListOf() }
                if (bucket.lastOrNull() != linkIndex) {
                    bucket += linkIndex
                }
            }
        }

        private fun collectCandidateIndexes(
            searchIndex: SearchIndex,
            state: SubscriptionsSearchState,
            terms: List<String>,
            normalizedTagFilter: String,
        ): IntArray {
            var candidates = searchIndex.allIndexes

            if (state.onlyTagged) {
                candidates = intersectSorted(candidates, searchIndex.taggedIndexes)
            }
            if (state.hasFiltersOnly) {
                candidates = intersectSorted(candidates, searchIndex.filteredIndexes)
            }
            if (candidates.isEmpty()) {
                return candidates
            }
            if (normalizedTagFilter.isNotEmpty()) {
                candidates =
                    narrowBySubstring(
                        candidates = candidates,
                        value = normalizedTagFilter,
                        trigramIndex = searchIndex.tagTrigrams,
                    )
            }
            if (candidates.isEmpty()) {
                return candidates
            }

            terms.forEach { term ->
                when {
                    term.startsWith(TAG_PREFIX) -> {
                        val value = term.removePrefix(TAG_PREFIX)
                        if (value.isEmpty()) {
                            return IntArray(0)
                        }
                        candidates =
                            narrowBySubstring(
                                candidates = candidates,
                                value = value,
                                trigramIndex = searchIndex.tagTrigrams,
                            )
                    }

                    term.startsWith(FILTER_PREFIX) -> {
                        val value = term.removePrefix(FILTER_PREFIX)
                        if (value.isEmpty()) {
                            return IntArray(0)
                        }
                        candidates =
                            narrowBySubstring(
                                candidates = candidates,
                                value = value,
                                trigramIndex = searchIndex.filterTrigrams,
                            )
                    }

                    term.startsWith(URL_PREFIX) -> {
                        val value = term.removePrefix(URL_PREFIX)
                        if (value.isEmpty()) {
                            return IntArray(0)
                        }
                        candidates =
                            narrowBySubstring(
                                candidates = candidates,
                                value = value,
                                trigramIndex = searchIndex.urlTrigrams,
                            )
                    }

                    else -> {
                        candidates =
                            narrowBySubstring(
                                candidates = candidates,
                                value = term,
                                trigramIndex = searchIndex.allTrigrams,
                            )
                    }
                }
                if (candidates.isEmpty()) {
                    return candidates
                }
            }

            return candidates
        }

        private fun narrowBySubstring(
            candidates: IntArray,
            value: String,
            trigramIndex: Map<String, IntArray>,
        ): IntArray {
            if (value.length < TRIGRAM_LENGTH) {
                return candidates
            }
            val trigrams = extractTrigrams(value)
            var narrowed: IntArray? = null
            trigrams.forEach { trigram ->
                val matches = trigramIndex[trigram] ?: return IntArray(0)
                narrowed =
                    if (narrowed == null) {
                        matches
                    } else {
                        intersectSorted(requireNotNull(narrowed), matches)
                    }
                if (requireNotNull(narrowed).isEmpty()) {
                    return IntArray(0)
                }
            }
            return if (narrowed == null) {
                candidates
            } else {
                intersectSorted(candidates, requireNotNull(narrowed))
            }
        }

        private fun extractTrigrams(value: String): Set<String> {
            if (value.length < TRIGRAM_LENGTH) return emptySet()
            return buildSet {
                for (index in 0..(value.length - TRIGRAM_LENGTH)) {
                    add(value.substring(index, index + TRIGRAM_LENGTH))
                }
            }
        }

        private fun intersectSorted(
            left: IntArray,
            right: IntArray,
        ): IntArray {
            if (left.isEmpty() || right.isEmpty()) return IntArray(0)
            val result = ArrayList<Int>(minOf(left.size, right.size))
            var leftIndex = 0
            var rightIndex = 0
            while (leftIndex < left.size && rightIndex < right.size) {
                val leftValue = left[leftIndex]
                val rightValue = right[rightIndex]
                when {
                    leftValue == rightValue -> {
                        result += leftValue
                        leftIndex += 1
                        rightIndex += 1
                    }

                    leftValue < rightValue -> leftIndex += 1
                    else -> rightIndex += 1
                }
            }
            return result.toIntArray()
        }

        private fun matchesEntry(
            entry: IndexedLink,
            state: SubscriptionsSearchState,
            terms: List<String>,
            normalizedTagFilter: String,
        ): Boolean {
            if (state.onlyTagged && !entry.hasTags) {
                return false
            }
            if (state.hasFiltersOnly && !entry.hasFilters) {
                return false
            }
            if (normalizedTagFilter.isNotEmpty()) {
                val hasMatchingTag = entry.normalizedTags.any { tag -> tag.contains(normalizedTagFilter) }
                if (!hasMatchingTag) {
                    return false
                }
            }
            if (terms.isEmpty()) {
                return true
            }
            return terms.all { term ->
                when {
                    term.startsWith(TAG_PREFIX) -> {
                        val value = term.removePrefix(TAG_PREFIX)
                        value.isNotEmpty() && entry.normalizedTags.any { tag -> tag.contains(value) }
                    }

                    term.startsWith(FILTER_PREFIX) -> {
                        val value = term.removePrefix(FILTER_PREFIX)
                        value.isNotEmpty() && entry.normalizedFilters.any { filter -> filter.contains(value) }
                    }

                    term.startsWith(URL_PREFIX) -> {
                        val value = term.removePrefix(URL_PREFIX)
                        value.isNotEmpty() && entry.normalizedUrl.contains(value)
                    }

                    else -> {
                        entry.normalizedUrl.contains(term) ||
                            entry.normalizedTags.any { tag -> tag.contains(term) } ||
                            entry.normalizedFilters.any { filter -> filter.contains(term) }
                    }
                }
            }
        }

        private fun normalize(value: String): String = value.trim().lowercase()

        private companion object {
            const val TAG_PREFIX = "tag:"
            const val FILTER_PREFIX = "filter:"
            const val URL_PREFIX = "url:"
            const val TRIGRAM_LENGTH = 3
        }

        private data class IndexedLink(
            val link: TrackedLink,
            val normalizedUrl: String,
            val normalizedTags: List<String>,
            val normalizedFilters: List<String>,
            val hasTags: Boolean,
            val hasFilters: Boolean,
        )

        private data class SearchIndex(
            val entries: List<IndexedLink>,
            val allIndexes: IntArray,
            val taggedIndexes: IntArray,
            val filteredIndexes: IntArray,
            val allTrigrams: Map<String, IntArray>,
            val urlTrigrams: Map<String, IntArray>,
            val tagTrigrams: Map<String, IntArray>,
            val filterTrigrams: Map<String, IntArray>,
        )
    }
