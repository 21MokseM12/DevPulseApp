package com.devpulse.app.push

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppVisibilityTracker
    @Inject
    constructor() : AppVisibilityProvider, DefaultLifecycleObserver {
        private val isForeground = AtomicBoolean(false)

        init {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            isForeground.set(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            lifecycle.addObserver(this)
        }

        override fun onStart(owner: LifecycleOwner) {
            isForeground.set(true)
        }

        override fun onStop(owner: LifecycleOwner) {
            isForeground.set(false)
        }

        override fun isAppInForeground(): Boolean = isForeground.get()
    }

interface AppVisibilityProvider {
    fun isAppInForeground(): Boolean
}
