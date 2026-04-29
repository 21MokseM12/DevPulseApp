package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ApiErrorResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
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

    suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto>
}

@Singleton
class DefaultDevPulseRemoteDataSource
    @Inject
    constructor(
        private val api: DevPulseApi,
        moshi: Moshi,
        private val apiErrorMapper: ApiErrorMapper,
    ) : DevPulseRemoteDataSource {
        private val apiErrorAdapter = moshi.adapter(ApiErrorResponseDto::class.java)

        override suspend fun registerClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return executeUnit { api.registerClient(request) }
        }

        override suspend fun unregisterClient(request: ClientCredentialsRequestDto): RemoteCallResult<Unit> {
            return executeUnit { api.unregisterClient(request) }
        }

        override suspend fun getLinks(): RemoteCallResult<List<LinkResponseDto>> {
            return execute { api.getLinks() }
        }

        override suspend fun addLink(request: AddLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            return execute { api.addLink(request) }
        }

        override suspend fun removeLink(request: RemoveLinkRequestDto): RemoteCallResult<LinkResponseDto> {
            return execute { api.removeLink(request) }
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
    }
