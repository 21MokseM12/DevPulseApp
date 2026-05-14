package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStoreNotificationPreferencesStore
import com.devpulse.app.data.local.preferences.NotificationPreferencesStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPreferencesStoreModule {
    @Binds
    @Singleton
    abstract fun bindNotificationPreferencesStore(
        implementation: DataStoreNotificationPreferencesStore,
    ): NotificationPreferencesStore
}
