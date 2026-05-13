package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ApiErrorResponseDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import com.squareup.moshi.Moshi
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

interface DevPulseRemoteDataSource {
    suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit>

    suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit>

    suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>>

    suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto>

    suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto>

    suspend fun getNotifications(
        limit: Int,
        offset: Int,
        tags: List<String>,
    ): RemoteCallResult<NotificationListResponseDto>

    suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto>

    suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto>
}

@Singleton
class DefaultDevPulseRemoteDataSource
    @Inject
    constructor(
        private val api: DevPulseApi,
        moshi: Moshi,
        private val apiErrorMapper: ApiErrorMapper,
        private val authTransportSecurityGuard: AuthTransportSecurityGuard,
    ) : DevPulseRemoteDataSource {
        private val apiErrorAdapter = moshi.adapter(ApiErrorResponseDto::class.java)

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            authTransportSecurityViolation()?.let { return it }
            return executeUnit { api.registerClient(request) }
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            authTransportSecurityViolation()?.let { return it }
            return executeUnit { api.unregisterClient(request) }
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return execute { api.getLinks() }
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            return execute { api.addLink(request) }
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<BotApiMessageResponseDto> {
            return execute { api.removeLink(request) }
        }

        override suspend fun getNotifications(
            limit: Int,
            offset: Int,
            tags: List<String>,
        ): RemoteCallResult<NotificationListResponseDto> {
            return execute { api.getNotifications(limit = limit, offset = offset, tags = tags) }
        }

        override suspend fun getUnreadNotificationsCount(): RemoteCallResult<UnreadCountResponseDto> {
            return execute { api.getUnreadNotificationsCount() }
        }

        override suspend fun markNotificationsRead(request: MarkReadRequestDto): RemoteCallResult<MarkReadResponseDto> {
            return execute { api.markNotificationsRead(request = request) }
        }

        private suspend fun <T> execute(block: suspend () -> Response<T>): RemoteCallResult<T> {
            return try {
                val response = block()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        RemoteCallResult.Success(
                            data = body,
                            statusCode = response.code(),
                        )
                    } else {
                        val exception = IllegalStateException("Response body is null for code ${response.code()}")
                        RemoteCallResult.NetworkFailure(
                            error = apiErrorMapper.mapNetworkError(exception),
                            throwable = exception,
                        )
                    }
                } else {
                    val statusCode = response.code()
                    RemoteCallResult.ApiFailure(
                        error = apiErrorMapper.mapApiError(statusCode = statusCode, rawError = parseApiError(response)),
                        statusCode = statusCode,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ioException: IOException) {
                RemoteCallResult.NetworkFailure(
                    error = apiErrorMapper.mapNetworkError(ioException),
                    throwable = ioException,
                )
            } catch (exception: Exception) {
                RemoteCallResult.NetworkFailure(
                    error = apiErrorMapper.mapNetworkError(exception),
                    throwable = exception,
                )
            }
        }

        private suspend fun executeUnit(block: suspend () -> Response<Unit>): RemoteCallResult<Unit> {
            return try {
                val response = block()
                if (response.isSuccessful) {
                    RemoteCallResult.Success(
                        data = Unit,
                        statusCode = response.code(),
                    )
                } else {
                    val statusCode = response.code()
                    RemoteCallResult.ApiFailure(
                        error = apiErrorMapper.mapApiError(statusCode = statusCode, rawError = parseApiError(response)),
                        statusCode = statusCode,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ioException: IOException) {
                RemoteCallResult.NetworkFailure(
                    error = apiErrorMapper.mapNetworkError(ioException),
                    throwable = ioException,
                )
            } catch (exception: Exception) {
                RemoteCallResult.NetworkFailure(
                    error = apiErrorMapper.mapNetworkError(exception),
                    throwable = exception,
                )
            }
        }

        private fun parseApiError(response: Response<*>): ApiErrorResponseDto? {
            val errorBody = response.errorBody() ?: return null
            return runCatching {
                apiErrorAdapter.fromJson(errorBody.string())
            }.getOrNull()
        }

        private fun authTransportSecurityViolation(): RemoteCallResult.NetworkFailure? {
            val violation = authTransportSecurityGuard.getAuthTransportViolation() ?: return null
            return RemoteCallResult.NetworkFailure(
                error = violation,
                throwable = IllegalStateException(violation.technicalDescription ?: violation.userMessage),
            )
        }
    }
