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
import javax.inject.Inject

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val links: List<TrackedLink> = emptyList(),
    val errorMessage: String? = null,
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

        private fun load(isRefresh: Boolean) {
            val current = _uiState.value
            if (current.isLoading || current.isRefreshing) return

            _uiState.update { state ->
                state.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null,
                )
            }

            viewModelScope.launch {
                when (val result = subscriptionsRepository.getSubscriptions()) {
                    is SubscriptionsResult.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                links = result.links,
                                errorMessage = null,
                            )
                        }
                    }

                    is SubscriptionsResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(
                                isLoading = false,
                                isRefreshing = false,
                                links = emptyList(),
                                errorMessage = result.error.userMessage,
                            )
                        }
                    }
                }
            }
        }
    }
