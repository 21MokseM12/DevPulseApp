package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class PushTokenSyncAction {
    Register,
    Unregister,
}

data class PendingPushTokenSync(
    val action: PushTokenSyncAction,
    val token: String,
)

interface PushTokenSyncStateStore {
    suspend fun getPendingSync(): PendingPushTokenSync?

    suspend fun savePendingSync(sync: PendingPushTokenSync)

    suspend fun clearPendingSync()
}

@Singleton
class DataStorePushTokenSyncStateStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : PushTokenSyncStateStore {
        override suspend fun getPendingSync(): PendingPushTokenSync? {
            return dataStore.data
                .map { preferences ->
                    val token = preferences[PENDING_TOKEN_KEY]
                    val rawAction = preferences[PENDING_ACTION_KEY]
                    val action = rawAction?.let(::actionFromRaw)
                    if (token.isNullOrBlank() || action == null) {
                        null
                    } else {
                        PendingPushTokenSync(
                            action = action,
                            token = token,
                        )
                    }
                }.first()
        }

        override suspend fun savePendingSync(sync: PendingPushTokenSync) {
            dataStore.edit { preferences ->
                preferences[PENDING_TOKEN_KEY] = sync.token
                preferences[PENDING_ACTION_KEY] = sync.action.name
            }
        }

        override suspend fun clearPendingSync() {
            dataStore.edit { preferences ->
                preferences.remove(PENDING_TOKEN_KEY)
                preferences.remove(PENDING_ACTION_KEY)
            }
        }

        private fun actionFromRaw(rawAction: String): PushTokenSyncAction? {
            return PushTokenSyncAction.entries.firstOrNull { action ->
                action.name == rawAction
            }
        }

        private companion object {
            private val PENDING_TOKEN_KEY = stringPreferencesKey("push_token_sync_pending_token")
            private val PENDING_ACTION_KEY = stringPreferencesKey("push_token_sync_pending_action")
        }
    }
