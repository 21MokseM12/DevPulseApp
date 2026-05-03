package com.devpulse.app.ui.settings

import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.ui.main.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onPermissionRequestTriggered_marksPermissionAsRequested() {
        runTest {
            val store = FakeNotificationPermissionStore()
            val viewModel = SettingsViewModel(store)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.hasRequestedNotificationPermission)
            viewModel.onPermissionRequestTriggered()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasRequestedNotificationPermission)
        }
    }

    private class FakeNotificationPermissionStore : NotificationPermissionStore {
        private val requested = MutableStateFlow(false)

        override fun observeHasRequested(): Flow<Boolean> = requested.asStateFlow()

        override suspend fun hasRequested(): Boolean = requested.value

        override suspend fun markRequested() {
            requested.value = true
        }
    }
}
