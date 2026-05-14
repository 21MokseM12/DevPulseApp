package com.devpulse.app.di

import com.devpulse.app.BuildConfig
import com.devpulse.app.data.remote.ApiErrorMapper
import com.devpulse.app.data.remote.AuthTransportSecurityGuard
import com.devpulse.app.data.remote.BuildConfigAuthTransportSecurityGuard
import com.devpulse.app.data.remote.ClientLoginHeaderInterceptor
import com.devpulse.app.data.remote.DEBUG_ENVIRONMENT
import com.devpulse.app.data.remote.DevPulseApi
import com.devpulse.app.data.remote.NetworkPolicyDefaults
import com.devpulse.app.data.remote.RetryPolicyInterceptor
import com.devpulse.app.data.remote.resolveTransportPinsForEnvironment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val level =
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        return createLoggingInterceptor(level)
    }

    @Provides
    @Singleton
    fun provideAuthTransportSecurityGuard(): AuthTransportSecurityGuard = BuildConfigAuthTransportSecurityGuard()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        clientLoginHeaderInterceptor: ClientLoginHeaderInterceptor,
    ): OkHttpClient {
        val certificatePinner =
            createCertificatePinner(
                baseUrl = BuildConfig.BASE_URL,
                environment = BuildConfig.ENVIRONMENT,
                releasePinsConfig = BuildConfig.RELEASE_CERT_PINS,
                stagingPinsConfig = BuildConfig.STAGING_CERT_PINS,
            )
        enforceProductionPinningPolicy(
            baseUrl = BuildConfig.BASE_URL,
            environment = BuildConfig.ENVIRONMENT,
            hasCertificatePinner = certificatePinner != null,
        )
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(NetworkPolicyDefaults.timeouts.connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(NetworkPolicyDefaults.timeouts.readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(NetworkPolicyDefaults.timeouts.writeTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(NetworkPolicyDefaults.timeouts.callTimeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(clientLoginHeaderInterceptor)
                .addInterceptor(RetryPolicyInterceptor())
                .addInterceptor(loggingInterceptor)
        certificatePinner?.let { builder.certificatePinner(it) }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideApiErrorMapper(): ApiErrorMapper = ApiErrorMapper()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideDevPulseApi(retrofit: Retrofit): DevPulseApi {
        return retrofit.create(DevPulseApi::class.java)
    }
}

internal fun createLoggingInterceptor(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor {
    val defaultLogger = HttpLoggingInterceptor.Logger.DEFAULT
    val redactingLogger =
        HttpLoggingInterceptor.Logger { rawMessage ->
            defaultLogger.log(redactSensitiveLogData(rawMessage))
        }
    return HttpLoggingInterceptor(redactingLogger).apply {
        sensitiveHeadersForRedaction().forEach(::redactHeader)
        this.level = level
    }
}

internal fun sensitiveHeadersForRedaction(): Set<String> {
    return setOf("Authorization", "Cookie", "Set-Cookie", "Client-Login")
}

internal fun createCertificatePinner(
    baseUrl: String,
    environment: String,
    releasePinsConfig: String,
    stagingPinsConfig: String,
): CertificatePinner? {
    if (environment.equals(DEBUG_ENVIRONMENT, ignoreCase = true)) {
        return null
    }
    val host = baseUrl.toHttpUrlOrNull()?.host ?: return null
    val configuredPins = resolveTransportPinsForEnvironment(environment, releasePinsConfig, stagingPinsConfig)
    if (configuredPins.isEmpty()) {
        return null
    }
    val pinHosts = resolvePinnedHosts(host)
    val builder = CertificatePinner.Builder()
    configuredPins.forEach { pin ->
        pinHosts.forEach { pinHost ->
            builder.add(pinHost, pin)
        }
    }
    return builder.build()
}

internal fun resolvePinnedHosts(baseHost: String): Set<String> {
    if (baseHost.equals("localhost", ignoreCase = true)) {
        return setOf(baseHost)
    }
    if (IPV4_HOST_REGEX.matches(baseHost) || baseHost.contains(":")) {
        return setOf(baseHost)
    }
    if (!baseHost.contains(".")) {
        return setOf(baseHost)
    }
    return linkedSetOf(baseHost, "*.$baseHost")
}

private val IPV4_HOST_REGEX = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")

private val SENSITIVE_QUERY_KEYS =
    setOf("password", "login", "token", "access_token", "refresh_token", "api_key", "secret")
private val SENSITIVE_JSON_KEYS =
    setOf("password", "login", "token", "accessToken", "refreshToken", "refresh_token", "apiKey", "secret")
private val SENSITIVE_BEARER_REGEX = Regex("(?i)(Bearer\\s+)([^\\s\"]+)")
private val SENSITIVE_BASIC_REGEX = Regex("(?i)(Basic\\s+)([^\\s\"]+)")

internal fun redactSensitiveLogData(raw: String): String {
    var result = raw
    SENSITIVE_QUERY_KEYS.forEach { key ->
        val pattern = Regex("(?i)(\\b${Regex.escape(key)}=)([^&\\s]+)")
        result = result.replace(pattern, "$1***")
    }
    SENSITIVE_JSON_KEYS.forEach { key ->
        val pattern = Regex("(?i)(\"$key\"\\s*:\\s*\")(.*?)(\")")
        result = result.replace(pattern, "$1***$3")
    }
    result = result.replace(SENSITIVE_BEARER_REGEX, "$1***")
    result = result.replace(SENSITIVE_BASIC_REGEX, "$1***")
    return result
}

internal fun enforceProductionPinningPolicy(
    baseUrl: String,
    environment: String,
    hasCertificatePinner: Boolean,
) {
    if (environment.equals(DEBUG_ENVIRONMENT, ignoreCase = true)) {
        return
    }
    val isHttps = baseUrl.toHttpUrlOrNull()?.isHttps == true
    if (!isHttps) {
        throw IllegalStateException("HTTPS is required for $environment builds.")
    }
    if (isHttps && !hasCertificatePinner) {
        throw IllegalStateException("TLS pinning must be configured for $environment builds.")
    }
}
