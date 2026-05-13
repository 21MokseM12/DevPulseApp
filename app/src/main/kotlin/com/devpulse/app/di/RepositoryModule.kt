package com.devpulse.app.di

import com.devpulse.app.data.repository.DefaultAppBootstrapRepository
import com.devpulse.app.data.repository.DefaultNotificationsRepository
import com.devpulse.app.data.repository.DefaultSubscriptionsRepository
import com.devpulse.app.data.repository.DefaultUpdatesRepository
import com.devpulse.app.domain.repository.AppBootstrapRepository
import com.devpulse.app.domain.repository.NotificationsRepository
import com.devpulse.app.domain.repository.SubscriptionsRepository
import com.devpulse.app.domain.repository.UpdatesRepository
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

    @Binds
    @Singleton
    abstract fun bindNotificationsRepository(implementation: DefaultNotificationsRepository): NotificationsRepository

    @Binds
    @Singleton
    abstract fun bindUpdatesRepository(implementation: DefaultUpdatesRepository): UpdatesRepository
}
