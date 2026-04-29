package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStoreSessionStore
import com.devpulse.app.data.local.preferences.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionStoreModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: DataStoreSessionStore): SessionStore
}
