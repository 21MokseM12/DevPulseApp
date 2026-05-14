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
                releasePinsConfig = BuildConfig.RELEASE_CERT_PINS,
                stagingPinsConfig = BuildConfig.STAGING_CERT_PINS,
            )
        }
    }

internal fun createAuthTransportViolation(
    baseUrl: String,
    environment: String,
    releasePinsConfig: String,
    stagingPinsConfig: String,
): ApiError? {
    val scheme = baseUrl.toHttpUrlOrNull()?.scheme
    val isSecure = scheme == "https"
    val isDebugEnvironment = environment.equals(DEBUG_ENVIRONMENT, ignoreCase = true)
    if (!isSecure && !isDebugEnvironment) {
        return ApiError(
            kind = ApiErrorKind.Configuration,
            userMessage = "Авторизация недоступна: небезопасный адрес сервера.",
            technicalDescription = "Auth over non-HTTPS base URL is blocked for $environment.",
        )
    }

    if (isDebugEnvironment) {
        return null
    }

    val hasPinsConfigured =
        resolveTransportPinsForEnvironment(
            environment = environment,
            releasePinsConfig = releasePinsConfig,
            stagingPinsConfig = stagingPinsConfig,
        ).isNotEmpty()
    if (!hasPinsConfigured) {
        return ApiError(
            kind = ApiErrorKind.Configuration,
            userMessage = "Авторизация недоступна: не настроены TLS pin-ы.",
            technicalDescription = "Auth is blocked for $environment: no valid TLS pins configured.",
        )
    }
    return null
}
