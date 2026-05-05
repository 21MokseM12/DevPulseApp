package com.devpulse.app.data.remote

import com.devpulse.app.BuildConfig
import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface AuthTransportSecurityGuard {
    fun getAuthTransportViolation(): ApiError?
}

@Singleton
class BuildConfigAuthTransportSecurityGuard
    @Inject
    constructor() : AuthTransportSecurityGuard {
        override fun getAuthTransportViolation(): ApiError? {
            return createAuthTransportViolation(
                baseUrl = BuildConfig.BASE_URL,
                environment = BuildConfig.ENVIRONMENT,
            )
        }
    }

internal fun createAuthTransportViolation(
    baseUrl: String,
    environment: String,
): ApiError? {
    val scheme = baseUrl.toHttpUrlOrNull()?.scheme
    val isSecure = scheme == "https"
    val isDebugEnvironment = environment.equals(DEBUG_ENVIRONMENT, ignoreCase = true)
    if (isSecure || isDebugEnvironment) {
        return null
    }
    return ApiError(
        kind = ApiErrorKind.Configuration,
        userMessage = "Авторизация недоступна: небезопасный адрес сервера.",
        technicalDescription = "Auth over non-HTTPS base URL is blocked for $environment.",
    )
}

private const val DEBUG_ENVIRONMENT = "debug"
