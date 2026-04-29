package com.devpulse.app.di

import com.devpulse.app.data.repository.DefaultAppBootstrapRepository
import com.devpulse.app.data.repository.DefaultSubscriptionsRepository
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.repository.SubscriptionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppBootstrapRepository(implementation: DefaultAppBootstrapRepository): AppBootstrapRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionsRepository(implementation: DefaultSubscriptionsRepository): SubscriptionsRepository
}
