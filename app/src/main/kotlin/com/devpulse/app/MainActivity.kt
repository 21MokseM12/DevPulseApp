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
    private val openUpdatesDigestContextRequest = mutableStateOf<OpenUpdatesDigestContextRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateOpenUpdatesFlag(intent)
        setContent {
            DevPulseApp(
                openUpdatesRequest = openUpdatesRequest.value,
                openUpdatesDigestContextRequest = openUpdatesDigestContextRequest.value,
                onOpenUpdatesHandled = { openUpdatesRequest.value = false },
                onOpenUpdatesDigestContextHandled = { openUpdatesDigestContextRequest.value = null },
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
        openUpdatesDigestContextRequest.value =
            mergeDigestContextRequest(
                current = openUpdatesDigestContextRequest.value,
                incoming = intent?.toDigestContextRequest(),
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

data class OpenUpdatesDigestContextRequest(
    val unreadOnly: Boolean,
    val periodStartEpochMs: Long?,
    val periodEndEpochMs: Long?,
)

internal fun Intent.toDigestContextRequest(): OpenUpdatesDigestContextRequest? {
    val hasDigestWindow =
        hasExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_START_EPOCH_MS) &&
            hasExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_END_EPOCH_MS)
    val periodStart = getDigestPeriodStartEpochMs(hasDigestWindow)
    val periodEnd = getDigestPeriodEndEpochMs(hasDigestWindow)
    return digestContextRequestFromExtras(
        unreadOnly = hasUnreadFilterExtra(),
        periodStartEpochMs = periodStart,
        periodEndEpochMs = periodEnd,
    )
}

internal fun digestContextRequestFromExtras(
    unreadOnly: Boolean,
    periodStartEpochMs: Long?,
    periodEndEpochMs: Long?,
): OpenUpdatesDigestContextRequest? {
    if (!unreadOnly && periodStartEpochMs == null && periodEndEpochMs == null) return null
    return OpenUpdatesDigestContextRequest(
        unreadOnly = unreadOnly,
        periodStartEpochMs = periodStartEpochMs,
        periodEndEpochMs = periodEndEpochMs,
    )
}

internal fun Intent.getDigestPeriodStartEpochMs(hasDigestWindow: Boolean): Long? {
    return if (hasDigestWindow) {
        getLongExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_START_EPOCH_MS, 0L)
    } else {
        null
    }
}

internal fun Intent.getDigestPeriodEndEpochMs(hasDigestWindow: Boolean): Long? {
    return if (hasDigestWindow) {
        getLongExtra(PushNotificationNavigation.EXTRA_DIGEST_PERIOD_END_EPOCH_MS, 0L)
    } else {
        null
    }
}

internal fun mergeDigestContextRequest(
    current: OpenUpdatesDigestContextRequest?,
    incoming: OpenUpdatesDigestContextRequest?,
): OpenUpdatesDigestContextRequest? {
    if (incoming == null) return current
    return incoming
}
