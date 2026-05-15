package com.devpulse.app.domain.repository

import com.devpulse.app.domain.model.ApiError

sealed interface AuthResult {
    data object Success : AuthResult

    data class Failure(
        val error: ApiError,
    ) : AuthResult
}

interface AuthRepository {
    suspend fun login(
        login: String,
        password: String,
    ): AuthResult

    suspend fun register(
        login: String,
        password: String,
    ): AuthResult
}
