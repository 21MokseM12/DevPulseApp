package com.devpulse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.devpulse.app.push.PushAnalyticsTracker
import com.devpulse.app.push.PushNotificationNavigation
import com.devpulse.app.ui.DevPulseApp
import dagger.hilt.android.AndroidEntryPoint
import java.net.URI
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var pushAnalyticsLogger: PushAnalyticsTracker

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
        val pushOpenRequest = intent?.toPushOpenRequest() ?: return
        pushAnalyticsLogger.pushOpened(
            eventId = pushOpenRequest.eventId,
            url = pushOpenRequest.url,
        )
        openUrlIfNeeded(pushOpenRequest.url)
        intent.removeExtra(PushNotificationNavigation.EXTRA_PUSH_EVENT_ID)
        intent.removeExtra(PushNotificationNavigation.EXTRA_PUSH_URL)
    }

    private fun openUrlIfNeeded(url: String?) {
        val normalizedUrl = url?.trim()?.takeIf { isValidHttpUri(it) } ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)))
        }
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

data class PushOpenRequest(
    val eventId: String?,
    val url: String?,
)

internal fun Intent.toPushOpenRequest(): PushOpenRequest? {
    return pushOpenRequestFromExtras(
        eventIdRaw = getStringExtra(PushNotificationNavigation.EXTRA_PUSH_EVENT_ID),
        urlRaw = getStringExtra(PushNotificationNavigation.EXTRA_PUSH_URL),
    )
}

internal fun pushOpenRequestFromExtras(
    eventIdRaw: String?,
    urlRaw: String?,
): PushOpenRequest? {
    val eventId = eventIdRaw?.trim()?.takeIf { it.isNotBlank() }
    val url = urlRaw?.trim()?.takeIf { it.isNotBlank() }
    if (eventId == null && url == null) {
        return null
    }
    return PushOpenRequest(
        eventId = eventId,
        url = url,
    )
}

internal fun isValidHttpUri(value: String): Boolean {
    return runCatching { URI(value.trim()) }
        .map { uri ->
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
}
