package com.devpulse.app.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.SubscriptionsSearchState
import com.devpulse.app.domain.model.SubscriptionsSortMode
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import com.devpulse.app.domain.usecase.ApplySubscriptionsSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAdding: Boolean = false,
    val isRemoving: Boolean = false,
    val allLinks: List<TrackedLink> = emptyList(),
    val links: List<TrackedLink> = emptyList(),
    val searchState: SubscriptionsSearchState = SubscriptionsSearchState(),
    val availableTags: List<String> = emptyList(),
    val isStaleData: Boolean = false,
    val lastSyncAtEpochMs: Long? = null,
    val errorMessage: String? = null,
    val removeErrorMessage: String? = null,
    val addLinkInput: String = "",
    val addTagsInput: String = "",
    val addFiltersInput: String = "",
    val addErrorMessage: String? = null,
    val pendingRemoval: TrackedLink? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val subscriptionsRepository: SubscriptionsRepository,
        private val applySubscriptionsSearchUseCase: ApplySubscriptionsSearchUseCase =
            ApplySubscriptionsSearchUseCase(),
        private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) : ViewModel() {
        private val restoredSearchState = restoreSearchState()
        private val searchInput = MutableStateFlow(restoredSearchState.query)
        private var debouncedNormalizedQuery = normalizeQuery(restoredSearchState.query)

        private val _uiState = MutableStateFlow(SubscriptionsUiState(searchState = restoredSearchState))
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        init {
            observeSearchQuery()
            load(isRefresh = false)
        }

        fun retry() {
            load(isRefresh = false)
        }

        fun refresh() {
            load(isRefresh = true)
        }

        fun onSearchQueryChanged(value: String) {
            _uiState.update { state ->
                state.copy(searchState = state.searchState.copy(query = value))
            }
            persistSearchState(_uiState.value.searchState)
            searchInput.value = value
        }

        fun onTagFilterSelected(tag: String?) {
            updateSearchState(
                transform = { current ->
                    current.copy(tagFilter = tag)
                },
            )
        }

        fun onOnlyTaggedPresetToggled() {
            updateSearchState(
                transform = { current ->
                    current.copy(onlyTagged = !current.onlyTagged)
                },
            )
        }

        fun onWithFiltersPresetToggled() {
            updateSearchState(
                transform = { current ->
                    current.copy(hasFiltersOnly = !current.hasFiltersOnly)
                },
            )
        }

        fun onSortModeSelected(mode: SubscriptionsSortMode) {
            updateSearchState(
                transform = { current ->
                    current.copy(sortMode = mode)
                },
            )
        }

        fun clearSearch() {
            val clearedState = SubscriptionsSearchState()
            _uiState.update { state ->
                state.copy(searchState = clearedState)
            }
            persistSearchState(clearedState)
            searchInput.value = ""
        }

        fun onAddLinkInputChanged(value: String) {
            _uiState.update { state ->
                state.copy(addLinkInput = value, addErrorMessage = null)
            }
        }

        fun onAddTagsInputChanged(value: String) {
            _uiState.update { state ->
                state.copy(addTagsInput = value, addErrorMessage = null)
            }
        }

        fun onAddFiltersInputChanged(value: String) {
            _uiState.update { state ->
                state.copy(addFiltersInput = value, addErrorMessage = null)
            }
        }

        fun onRemoveRequested(link: TrackedLink) {
            _uiState.update { state ->
                state.copy(
                    pendingRemoval = link,
                    removeErrorMessage = null,
                )
            }
        }

        fun onRemoveDismissed() {
            _uiState.update { state ->
                state.copy(pendingRemoval = null)
            }
        }

        fun confirmRemove() {
            val current = _uiState.value
            val target = current.pendingRemoval ?: return
            if (current.isRemoving || current.isLoading || current.isRefreshing || current.isAdding) return

            val oldLinks = current.allLinks
            val index = oldLinks.indexOfFirst { it.id == target.id }
            if (index == -1) {
                _uiState.update { state -> state.copy(pendingRemoval = null) }
                return
            }
            val optimisticLinks = oldLinks.filterNot { it.id == target.id }
            _uiState.update { state ->
                state.copy(
                    isRemoving = true,
                    allLinks = optimisticLinks,
                    pendingRemoval = null,
                    removeErrorMessage = null,
                )
            }
            recomputeVisibleLinks()

            viewModelScope.launch {
                when (val result = subscriptionsRepository.removeSubscription(link = target.url)) {
                    is SubscriptionsResult.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                isRemoving = false,
                                isStaleData = result.isStale,
                                lastSyncAtEpochMs = result.lastSyncAtEpochMs,
                            )
                        }
                    }

                    is SubscriptionsResult.Failure -> {
                        _uiState.update { state ->
                            val rollbackLinks = state.allLinks.toMutableList().apply { add(index, target) }
                            state.copy(
                                isRemoving = false,
                                allLinks = rollbackLinks,
                                removeErrorMessage = result.error.userMessage,
                            )
                        }
                        recomputeVisibleLinks()
                    }
                }
            }
        }

        fun addSubscription() {
            val current = _uiState.value
            if (current.isAdding || current.isLoading || current.isRefreshing) return

            val link = current.addLinkInput.trim()
            val tags = parseCsv(current.addTagsInput)
            val filters = parseCsv(current.addFiltersInput)

            if (!isValidHttpUri(link)) {
                _uiState.update { state ->
                    state.copy(addErrorMessage = "Введите корректный URL (http/https).")
                }
                return
            }

            _uiState.update { state ->
                state.copy(
                    isAdding = true,
                    addErrorMessage = null,
                    removeErrorMessage = null,
                )
            }

            viewModelScope.launch {
                when (
                    val result =
                        subscriptionsRepository.addSubscription(
                            link = link,
                            tags = tags,
                            filters = filters,
                        )
                ) {
                    is SubscriptionsResult.Success -> {
                        val added = result.links.firstOrNull()
                        _uiState.update { state ->
                            val updatedLinks = mergeAddedLink(state.allLinks, added)
                            state.copy(
                                isAdding = false,
                                allLinks = updatedLinks,
                                addLinkInput = "",
                                addTagsInput = "",
                                addFiltersInput = "",
                                addErrorMessage = null,
                                isStaleData = result.isStale,
                                lastSyncAtEpochMs = result.lastSyncAtEpochMs,
                            )
                        }
                        recomputeVisibleLinks()
                    }

                    is SubscriptionsResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(
                                isAdding = false,
                                addErrorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
            }
        }

        private fun load(isRefresh: Boolean) {
            val current = _uiState.value
            if (current.isLoading || current.isRefreshing) return

            _uiState.update { state ->
                state.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null,
                    removeErrorMessage = null,
                )
            }

            viewModelScope.launch {
                when (val result = subscriptionsRepository.getSubscriptions(forceRefresh = isRefresh)) {
                    is SubscriptionsResult.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                allLinks = result.links,
                                isStaleData = result.isStale,
                                lastSyncAtEpochMs = result.lastSyncAtEpochMs,
                                errorMessage = null,
                            )
                        }
                        recomputeVisibleLinks()
                        if (!isRefresh && result.isStale) {
                            load(isRefresh = true)
                        }
                    }

                    is SubscriptionsResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
            }
        }

        private fun parseCsv(input: String): List<String> {
            return input
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        private fun isValidHttpUri(value: String): Boolean {
            return runCatching { URI(value) }
                .map { uri ->
                    val scheme = uri.scheme?.lowercase()
                    (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
                }.getOrDefault(false)
        }

        private fun mergeAddedLink(
            links: List<TrackedLink>,
            added: TrackedLink?,
        ): List<TrackedLink> {
            if (added == null) {
                return links
            }
            val normalizedUrl = normalizeQuery(added.url)
            return listOf(added) + links.filterNot { normalizeQuery(it.url) == normalizedUrl }
        }

        private fun observeSearchQuery() {
            viewModelScope.launch {
                searchInput
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { query ->
                        debouncedNormalizedQuery = normalizeQuery(query)
                        recomputeVisibleLinks()
                    }
            }
        }

        private fun recomputeVisibleLinks() {
            _uiState.update { state ->
                val availableTags = collectAvailableTags(state.allLinks)
                val filteredLinks =
                    applySubscriptionsSearchUseCase(
                        links = state.allLinks,
                        state = state.searchState.copy(query = debouncedNormalizedQuery),
                    )
                state.copy(
                    links = filteredLinks,
                    availableTags = availableTags,
                )
            }
        }

        private fun collectAvailableTags(links: List<TrackedLink>): List<String> {
            return links
                .flatMap { it.tags }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }

        private fun updateSearchState(transform: (SubscriptionsSearchState) -> SubscriptionsSearchState) {
            _uiState.update { state ->
                val updatedSearchState = transform(state.searchState)
                persistSearchState(updatedSearchState)
                state.copy(searchState = updatedSearchState)
            }
            recomputeVisibleLinks()
        }

        private fun restoreSearchState(): SubscriptionsSearchState {
            val sortModeName =
                savedStateHandle.get<String>(KEY_SEARCH_SORT_MODE)
                    ?: SubscriptionsSortMode.RECENTLY_ADDED.name
            val sortMode =
                SubscriptionsSortMode.entries.firstOrNull { it.name == sortModeName }
                    ?: SubscriptionsSortMode.RECENTLY_ADDED
            return SubscriptionsSearchState(
                query = savedStateHandle.get<String>(KEY_SEARCH_QUERY).orEmpty(),
                tagFilter = savedStateHandle.get<String?>(KEY_SEARCH_TAG_FILTER),
                hasFiltersOnly = savedStateHandle.get<Boolean>(KEY_SEARCH_HAS_FILTERS_ONLY) ?: false,
                onlyTagged = savedStateHandle.get<Boolean>(KEY_SEARCH_ONLY_TAGGED) ?: false,
                sortMode = sortMode,
            )
        }

        private fun persistSearchState(state: SubscriptionsSearchState) {
            savedStateHandle[KEY_SEARCH_QUERY] = state.query
            savedStateHandle[KEY_SEARCH_TAG_FILTER] = state.tagFilter
            savedStateHandle[KEY_SEARCH_HAS_FILTERS_ONLY] = state.hasFiltersOnly
            savedStateHandle[KEY_SEARCH_ONLY_TAGGED] = state.onlyTagged
            savedStateHandle[KEY_SEARCH_SORT_MODE] = state.sortMode.name
        }

        private fun normalizeQuery(value: String): String = value.trim().lowercase()

        private companion object {
            const val SEARCH_DEBOUNCE_MS = 250L
            const val KEY_SEARCH_QUERY = "subscriptions_search_query"
            const val KEY_SEARCH_TAG_FILTER = "subscriptions_search_tag_filter"
            const val KEY_SEARCH_HAS_FILTERS_ONLY = "subscriptions_search_has_filters_only"
            const val KEY_SEARCH_ONLY_TAGGED = "subscriptions_search_only_tagged"
            const val KEY_SEARCH_SORT_MODE = "subscriptions_search_sort_mode"
        }
    }
