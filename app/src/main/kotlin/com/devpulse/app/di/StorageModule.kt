package com.devpulse.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            context.preferencesDataStoreFile("devpulse.preferences_pb")
        }
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
