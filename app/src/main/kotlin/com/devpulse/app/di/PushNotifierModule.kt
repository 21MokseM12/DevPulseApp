package com.devpulse.app.di

import com.devpulse.app.push.PushNotificationManager
import com.devpulse.app.push.PushNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushNotifierModule {
    @Binds
    @Singleton
    abstract fun bindPushNotifier(manager: PushNotificationManager): PushNotifier
}
