package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.NotificationDigestMode
import com.devpulse.app.data.local.preferences.NotificationPresentationMode

interface PushNotifier {
    fun showUpdateNotification(
        update: ParsedPushUpdate,
        presentationMode: NotificationPresentationMode,
        digestMode: NotificationDigestMode?,
    )
}
