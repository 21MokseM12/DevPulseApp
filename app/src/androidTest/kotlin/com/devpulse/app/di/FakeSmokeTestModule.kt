package com.devpulse.app.di

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.testing.FakeSmokeRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataSourceModule::class],
)
abstract class FakeSmokeTestModule {
    @Binds
    @Singleton
    abstract fun bindDevPulseRemoteDataSource(implementation: FakeSmokeRemoteDataSource): DevPulseRemoteDataSource
}
