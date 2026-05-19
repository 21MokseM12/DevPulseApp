package com.devpulse.app

import android.app.Application
import com.devpulse.app.push.AppVisibilityTracker
import com.devpulse.app.push.PushInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DevPulseApplication : Application() {
    @Inject
    lateinit var pushInitializer: PushInitializer

    @Inject
    lateinit var appVisibilityTracker: AppVisibilityTracker

    override fun onCreate() {
        super.onCreate()
        appVisibilityTracker.isAppInForeground()
        pushInitializer.initialize()
    }
}
