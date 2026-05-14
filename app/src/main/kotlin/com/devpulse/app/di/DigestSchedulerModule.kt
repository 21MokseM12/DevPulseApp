package com.devpulse.app.di

import com.devpulse.app.push.DigestScheduler
import com.devpulse.app.push.WorkManagerDigestScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DigestSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindDigestScheduler(implementation: WorkManagerDigestScheduler): DigestScheduler
}
