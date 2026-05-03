package com.devpulse.app.ui.settings

import android.os.Build

enum class NotificationPermissionState {
    NotRequired,
    Granted,
    NeedsRequest,
    NeedsSettings,
}

class NotificationPermissionEvaluator {
    fun resolve(
        sdkInt: Int,
        hasRuntimePermission: Boolean,
        notificationsEnabled: Boolean,
        hasRequestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): NotificationPermissionState {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return NotificationPermissionState.NotRequired
        if (hasRuntimePermission && notificationsEnabled) return NotificationPermissionState.Granted
        if (!hasRuntimePermission) {
            if (!hasRequestedBefore) return NotificationPermissionState.NeedsRequest
            return if (shouldShowRationale) {
                NotificationPermissionState.NeedsRequest
            } else {
                NotificationPermissionState.NeedsSettings
            }
        }
        return NotificationPermissionState.NeedsSettings
    }
}
