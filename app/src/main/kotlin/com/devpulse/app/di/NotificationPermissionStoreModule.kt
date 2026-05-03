package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStoreNotificationPermissionStore
import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPermissionStoreModule {
    @Binds
    @Singleton
    abstract fun bindNotificationPermissionStore(
        implementation: DataStoreNotificationPermissionStore,
    ): NotificationPermissionStore
}
