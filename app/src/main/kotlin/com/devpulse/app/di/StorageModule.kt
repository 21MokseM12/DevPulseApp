package com.devpulse.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.devpulse.app.data.local.db.AppDatabase
import com.devpulse.app.data.local.db.DatabaseMigrations
import com.devpulse.app.data.local.db.PushUpdatesDao
import com.devpulse.app.data.local.db.SubscriptionsCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.devpulsePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "devpulse.preferences_pb",
)

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return context.devpulsePreferencesDataStore
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "devpulse.db",
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
    }

    @Provides
    @Singleton
    fun providePushUpdatesDao(database: AppDatabase): PushUpdatesDao = database.pushUpdatesDao()

    @Provides
    @Singleton
    fun provideSubscriptionsCacheDao(database: AppDatabase): SubscriptionsCacheDao = database.subscriptionsCacheDao()
}
