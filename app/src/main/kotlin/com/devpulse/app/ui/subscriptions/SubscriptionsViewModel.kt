package com.devpulse.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.TrackedLink
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.SubscriptionsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAdding: Boolean = false,
    val isRemoving: Boolean = false,
    val links: List<TrackedLink> = emptyList(),
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

@HiltViewModel
class SubscriptionsViewModel
    @Inject
    constructor(
        private val subscriptionsRepository: SubscriptionsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SubscriptionsUiState())
        val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

        init {
            load(isRefresh = false)
        }

        fun retry() {
            load(isRefresh = false)
        }

        fun refresh() {
            load(isRefresh = true)
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

            val oldLinks = current.links
            val index = oldLinks.indexOfFirst { it.id == target.id }
            if (index == -1) {
                _uiState.update { state -> state.copy(pendingRemoval = null) }
                return
            }
            val optimisticLinks = oldLinks.filterNot { it.id == target.id }
            _uiState.update { state ->
                state.copy(
                    isRemoving = true,
                    links = optimisticLinks,
                    pendingRemoval = null,
                    removeErrorMessage = null,
                )
            }

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
                            val rollbackLinks = state.links.toMutableList().apply { add(index, target) }
                            state.copy(
                                isRemoving = false,
                                links = rollbackLinks,
                                removeErrorMessage = result.error.userMessage,
                            )
                        }
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
                            state.copy(
                                isAdding = false,
                                links = if (added != null) listOf(added) + state.links else state.links,
                                addLinkInput = "",
                                addTagsInput = "",
                                addFiltersInput = "",
                                addErrorMessage = null,
                                isStaleData = result.isStale,
                                lastSyncAtEpochMs = result.lastSyncAtEpochMs,
                            )
                        }
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
                                links = result.links,
                                isStaleData = result.isStale,
                                lastSyncAtEpochMs = result.lastSyncAtEpochMs,
                                errorMessage = null,
                            )
                        }
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
    }
