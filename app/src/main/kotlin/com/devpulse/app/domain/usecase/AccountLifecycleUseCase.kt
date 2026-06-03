package com.devpulse.app.domain.usecase

import com.devpulse.app.data.local.preferences.NotificationPermissionStore
import com.devpulse.app.data.local.preferences.PushTokenStore
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import com.devpulse.app.domain.repository.UpdatesRepository
import com.devpulse.app.push.PushTokenSyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed interface AccountLifecycleResult {
    data object Success : AccountLifecycleResult

    data class Failure(
        val error: ApiError,
    ) : AccountLifecycleResult

    data object Cancelled : AccountLifecycleResult
}

@Singleton
open class AccountLifecycleUseCase
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
        private val sessionStore: SessionStore,
        private val updatesRepository: UpdatesRepository,
        private val pushTokenStore: PushTokenStore,
        private val notificationPermissionStore: NotificationPermissionStore,
        private val pushTokenSyncOrchestrator: PushTokenSyncCoordinator,
    ) {
        open suspend fun logout(): AccountLifecycleResult {
            clearLocalState(reason = "logout")
            return AccountLifecycleResult.Success
        }

        open suspend fun unregister(password: String): AccountLifecycleResult {
            val session = sessionStore.getSession()
            if (session == null) {
                clearLocalState(reason = "unregister_without_session")
                return AccountLifecycleResult.Success
            }

            return try {
                when (
                    val result =
                        remoteDataSource.unregisterClient(
                            ClientCredentialsRequestDto(
                                login = session.login,
                                password = password,
                            ),
                        )
                ) {
                    is RemoteCallResult.Success -> {
                        clearLocalState(reason = "unregister_success")
                        AccountLifecycleResult.Success
                    }

                    is RemoteCallResult.ApiFailure -> {
                        if (result.error.kind == ApiErrorKind.NotFound) {
                            clearLocalState(reason = "unregister_not_found")
                            AccountLifecycleResult.Success
                        } else {
                            AccountLifecycleResult.Failure(result.error)
                        }
                    }

                    is RemoteCallResult.NetworkFailure -> {
                        AccountLifecycleResult.Failure(result.error)
                    }
                }
            } catch (_: CancellationException) {
                AccountLifecycleResult.Cancelled
            }
        }

        private suspend fun clearLocalState(reason: String) {
            val currentToken = pushTokenStore.getToken()
            if (!currentToken.isNullOrBlank()) {
                pushTokenSyncOrchestrator.queueUnregister(
                    token = currentToken,
                    reason = reason,
                )
            }
            sessionStore.clearSession()
            updatesRepository.clearUpdates()
            notificationPermissionStore.clearRequestedFlag()
        }
    }
