package com.devpulse.app.data.repository

import com.devpulse.app.data.remote.DevPulseRemoteDataSource
import com.devpulse.app.data.remote.RemoteCallResult
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.domain.repository.AuthRepository
import com.devpulse.app.domain.repository.AuthResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthRepository
    @Inject
    constructor(
        private val remoteDataSource: DevPulseRemoteDataSource,
    ) : AuthRepository {
        override suspend fun login(
            login: String,
            password: String,
        ): AuthResult {
            return when (
                val result =
                    remoteDataSource.loginClient(
                        request =
                            ClientCredentialsRequestDto(
                                login = login,
                                password = password,
                            ),
                    )
            ) {
                is RemoteCallResult.Success -> AuthResult.Success
                is RemoteCallResult.ApiFailure -> AuthResult.Failure(error = result.error)
                is RemoteCallResult.NetworkFailure -> AuthResult.Failure(error = result.error)
            }
        }

        override suspend fun register(
            login: String,
            password: String,
        ): AuthResult {
            return when (
                val result =
                    remoteDataSource.registerClient(
                        request =
                            ClientCredentialsRequestDto(
                                login = login,
                                password = password,
                            ),
                    )
            ) {
                is RemoteCallResult.Success -> AuthResult.Success
                is RemoteCallResult.ApiFailure -> AuthResult.Failure(error = result.error)
                is RemoteCallResult.NetworkFailure -> AuthResult.Failure(error = result.error)
            }
        }
    }
