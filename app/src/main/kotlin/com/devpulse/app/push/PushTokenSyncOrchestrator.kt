package com.devpulse.app.push

import android.util.Log
import com.devpulse.app.data.local.preferences.PendingPushTokenSync
import com.devpulse.app.data.local.preferences.PushTokenSyncAction
import com.devpulse.app.data.local.preferences.PushTokenSyncStateStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.DeviceTokenRequestDto
import com.devpulse.app.data.remote.dto.PushTokenDeactivateRequestDto
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenSyncOrchestrator
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
        private val stateStore: PushTokenSyncStateStore,
        private val sessionStore: SessionStore,
        private val metadataProvider: PushTokenMetadataSource,
        private val analyticsLogger: PushAnalyticsTracker,
    ) : PushTokenSyncCoordinator {
        override suspend fun queueRegister(
            token: String,
            reason: String,
        ) {
            val normalizedToken = token.trim()
            if (normalizedToken.isEmpty()) return
            stateStore.savePendingSync(
                PendingPushTokenSync(
                    action = PushTokenSyncAction.Register,
                    token = normalizedToken,
                ),
            )
            logDebug("push_token_sync_queue action=register reason=$reason")
            syncPending(reason = reason)
        }

        override suspend fun queueUnregister(
            token: String,
            reason: String,
        ) {
            val normalizedToken = token.trim()
            if (normalizedToken.isEmpty()) return
            stateStore.savePendingSync(
                PendingPushTokenSync(
                    action = PushTokenSyncAction.Unregister,
                    token = normalizedToken,
                ),
            )
            logDebug("push_token_sync_queue action=unregister reason=$reason")
            syncPending(reason = reason)
        }

        override suspend fun syncPending(reason: String) {
            val pending = stateStore.getPendingSync() ?: return
            if (pending.action == PushTokenSyncAction.Register && !hasClientSession()) {
                logDebug(
                    "push_token_sync_result status=skipped action=register reason=no_client_session trigger=$reason",
                )
                return
            }
            val metadata = metadataProvider.getMetadata()
            val registerRequest =
                DeviceTokenRequestDto(
                    token = pending.token,
                    appVersion = metadata.appVersion,
                    deviceId = metadata.deviceId,
                )
            val unregisterRequest = PushTokenDeactivateRequestDto(token = pending.token)
            repeat(MAX_ATTEMPTS) { index ->
                val attempt = index + 1
                val result =
                    when (pending.action) {
                        PushTokenSyncAction.Register -> remoteDataSource.registerDeviceToken(registerRequest)
                        PushTokenSyncAction.Unregister -> remoteDataSource.unregisterDeviceToken(unregisterRequest)
                    }
                when (result) {
                    is RemoteCallResult.Success -> {
                        stateStore.clearPendingSync()
                        if (pending.action == PushTokenSyncAction.Register) {
                            analyticsLogger.tokenRegistered(
                                statusCode = result.statusCode,
                                reason = reason,
                                source = "push_token_sync",
                            )
                        }
                        logDebug(
                            "push_token_sync_result status=success action=${pending.action.name.lowercase()} " +
                                "code=${result.statusCode} attempt=$attempt reason=$reason",
                        )
                        return
                    }

                    is RemoteCallResult.ApiFailure -> {
                        val isRetryable = result.statusCode >= 500 || result.statusCode == 429
                        logWarn(
                            "push_token_sync_result status=api_failure action=${pending.action.name.lowercase()} " +
                                "code=${result.statusCode} retryable=$isRetryable attempt=$attempt reason=$reason",
                            null,
                        )
                        if (!isRetryable) {
                            stateStore.clearPendingSync()
                            return
                        }
                    }

                    is RemoteCallResult.NetworkFailure -> {
                        logWarn(
                            "push_token_sync_result status=network_failure action=${pending.action.name.lowercase()} " +
                                "error=${result.error.kind} attempt=$attempt reason=$reason",
                            result.throwable,
                        )
                    }
                }
                if (attempt < MAX_ATTEMPTS) {
                    delay(RETRY_DELAYS_MS[index])
                }
            }
        }

        private suspend fun hasClientSession(): Boolean {
            return !sessionStore.getSession()?.login.isNullOrBlank()
        }

        private companion object {
            const val LOG_TAG = "PushTokenSync"
            const val MAX_ATTEMPTS = 3
            val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
        }

        private fun logDebug(message: String) {
            runCatching { Log.d(LOG_TAG, message) }
        }

        private fun logWarn(
            message: String,
            throwable: Throwable?,
        ) {
            runCatching { Log.w(LOG_TAG, message, throwable) }
        }
    }

interface PushTokenSyncCoordinator {
    suspend fun queueRegister(
        token: String,
        reason: String,
    )

    suspend fun queueUnregister(
        token: String,
        reason: String,
    )

    suspend fun syncPending(reason: String)
}
