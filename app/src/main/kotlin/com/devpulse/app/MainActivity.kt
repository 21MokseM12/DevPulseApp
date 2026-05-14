package com.devpulse.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.devpulse.app.push.PushNotificationNavigation
import com.devpulse.app.ui.DevPulseApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val openUpdatesRequest = mutableStateOf(false)
    private val openUpdatesUnreadOnlyRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateOpenUpdatesFlag(intent)
        setContent {
            DevPulseApp(
                openUpdatesRequest = openUpdatesRequest.value,
                openUpdatesUnreadOnlyRequest = openUpdatesUnreadOnlyRequest.value,
                onOpenUpdatesHandled = { openUpdatesRequest.value = false },
                onOpenUpdatesUnreadOnlyHandled = { openUpdatesUnreadOnlyRequest.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateOpenUpdatesFlag(intent)
    }

    private fun updateOpenUpdatesFlag(intent: Intent?) {
        openUpdatesRequest.value =
            mergeOpenUpdatesRequest(
                current = openUpdatesRequest.value,
                hasOpenUpdatesExtra = intent?.hasOpenUpdatesExtra() == true,
            )
        openUpdatesUnreadOnlyRequest.value =
            mergeOpenUpdatesRequest(
                current = openUpdatesUnreadOnlyRequest.value,
                hasOpenUpdatesExtra = intent?.hasUnreadFilterExtra() == true,
            )
    }
}

internal fun mergeOpenUpdatesRequest(
    current: Boolean,
    hasOpenUpdatesExtra: Boolean,
): Boolean {
    return current || hasOpenUpdatesExtra
}

internal fun Intent.hasOpenUpdatesExtra(): Boolean {
    return getBooleanExtra(PushNotificationNavigation.EXTRA_OPEN_UPDATES, false)
}

internal fun Intent.hasUnreadFilterExtra(): Boolean {
    return getBooleanExtra(PushNotificationNavigation.EXTRA_FILTER_UNREAD_ONLY, false)
}
