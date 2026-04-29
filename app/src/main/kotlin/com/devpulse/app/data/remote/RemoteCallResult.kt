package com.devpulse.app.data.remote

import com.devpulse.app.domain.model.ApiError

sealed interface RemoteCallResult<out T> {
    data class Success<T>(
        val data: T,
        val statusCode: Int,
    ) : RemoteCallResult<T>

    data class ApiFailure(
        val error: ApiError,
        val statusCode: Int,
    ) : RemoteCallResult<Nothing>

    data class NetworkFailure(
        val error: ApiError,
        val throwable: Throwable,
    ) : RemoteCallResult<Nothing>
}
