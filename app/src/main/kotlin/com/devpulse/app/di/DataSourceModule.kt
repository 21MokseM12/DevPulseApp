package com.devpulse.app.di

import com.devpulse.app.data.remote.DefaultDevPulseRemoteDataSource
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
    @Binds
    @Singleton
    abstract fun bindDevPulseRemoteDataSource(
        implementation: DefaultDevPulseRemoteDataSource,
    ): DevPulseRemoteDataSource
}
