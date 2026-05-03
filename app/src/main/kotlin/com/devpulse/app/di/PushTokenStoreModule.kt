package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStorePushTokenStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushTokenStoreModule {
    @Binds
    @Singleton
    abstract fun bindPushTokenStore(implementation: DataStorePushTokenStore): PushTokenStore
}
