package com.devpulse.app.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.repository.UpdatesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUiState(
    val isLoading: Boolean = true,
    val events: List<UpdateEvent> = emptyList(),
    val markingIds: Set<Long> = emptySet(),
    val actionErrorMessage: String? = null,
)

@HiltViewModel
class UpdatesViewModel
    @Inject
    constructor(
        private val updatesRepository: UpdatesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(UpdatesUiState())
        val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                updatesRepository.observeUpdates().collect { updates ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            events = updates,
                            actionErrorMessage = null,
                        )
                    }
                }
            }
        }

        fun markAsRead(updateId: Long) {
            val current = _uiState.value
            val target = current.events.firstOrNull { it.id == updateId } ?: return
            if (target.isRead || current.markingIds.contains(updateId)) return

            _uiState.update { state ->
                state.copy(
                    events =
                        state.events.map { event ->
                            if (event.id == updateId) event.copy(isRead = true) else event
                        },
                    markingIds = state.markingIds + updateId,
                    actionErrorMessage = null,
                )
            }

            viewModelScope.launch {
                val marked = updatesRepository.markAsRead(updateId)
                _uiState.update { state ->
                    val rollbackEvents =
                        if (marked) {
                            state.events
                        } else {
                            state.events.map { event ->
                                if (event.id == updateId) event.copy(isRead = false) else event
                            }
                        }
                    state.copy(
                        events = rollbackEvents,
                        markingIds = state.markingIds - updateId,
                        actionErrorMessage = if (marked) null else "Не удалось отметить событие прочитанным.",
                    )
                }
            }
        }
    }
