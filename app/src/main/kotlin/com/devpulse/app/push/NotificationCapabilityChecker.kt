package com.devpulse.app.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCapabilityChecker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : NotificationCapabilityProvider {
        override fun canPostNotifications(): Boolean {
            val manager = NotificationManagerCompat.from(context)
            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            return canPostNotifications(
                areNotificationsEnabled = manager.areNotificationsEnabled(),
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = permissionGranted,
            )
        }
    }

interface NotificationCapabilityProvider {
    fun canPostNotifications(): Boolean
}
