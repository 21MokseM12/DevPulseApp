package com.devpulse.app.di

import com.devpulse.app.push.AppVisibilityProvider
import com.devpulse.app.push.AppVisibilityTracker
import com.devpulse.app.push.NotificationCapabilityChecker
import com.devpulse.app.push.NotificationCapabilityProvider
import com.devpulse.app.push.PushTokenSyncCoordinator
import com.devpulse.app.push.PushTokenSyncOrchestrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushRuntimeStateModule {
    @Binds
    @Singleton
    abstract fun bindAppVisibilityProvider(implementation: AppVisibilityTracker): AppVisibilityProvider

    @Binds
    @Singleton
    abstract fun bindNotificationCapabilityProvider(
        implementation: NotificationCapabilityChecker,
    ): NotificationCapabilityProvider

    @Binds
    @Singleton
    abstract fun bindPushTokenSyncCoordinator(implementation: PushTokenSyncOrchestrator): PushTokenSyncCoordinator
}
