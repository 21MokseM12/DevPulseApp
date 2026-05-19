package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStorePushTokenSyncStateStore
import com.devpulse.app.data.local.preferences.PushTokenSyncStateStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushTokenSyncStateStoreModule {
    @Binds
    @Singleton
    abstract fun bindPushTokenSyncStateStore(implementation: DataStorePushTokenSyncStateStore): PushTokenSyncStateStore
}
