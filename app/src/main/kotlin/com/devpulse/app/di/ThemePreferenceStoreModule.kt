package com.devpulse.app.di

import com.devpulse.app.data.local.preferences.DataStoreThemePreferenceStore
import com.devpulse.app.data.local.preferences.ThemePreferenceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemePreferenceStoreModule {
    @Binds
    @Singleton
    abstract fun bindThemePreferenceStore(implementation: DataStoreThemePreferenceStore): ThemePreferenceStore
}
