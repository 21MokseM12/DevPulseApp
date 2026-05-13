package com.devpulse.app.di

import com.devpulse.app.BuildConfig
import com.devpulse.app.data.remote.ApiErrorMapper
import com.devpulse.app.data.remote.AuthTransportSecurityGuard
import com.devpulse.app.data.remote.BuildConfigAuthTransportSecurityGuard
import com.devpulse.app.data.remote.ClientLoginHeaderInterceptor
import com.devpulse.app.data.remote.DevPulseApi
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
        val builder =
            OkHttpClient.Builder()
                .addInterceptor(clientLoginHeaderInterceptor)
                .addInterceptor(loggingInterceptor)
        createCertificatePinner(
            baseUrl = BuildConfig.BASE_URL,
            environment = BuildConfig.ENVIRONMENT,
        )?.let { builder.certificatePinner(it) }
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
    return HttpLoggingInterceptor().apply {
        sensitiveHeadersForRedaction().forEach(::redactHeader)
        this.level = level
    }
}

internal fun sensitiveHeadersForRedaction(): Set<String> {
    return setOf("Authorization", "Cookie", "Set-Cookie", "Client-Login")
}

private val SENSITIVE_QUERY_KEYS = setOf("password", "token", "access_token", "refresh_token", "api_key", "secret")
private val SENSITIVE_JSON_KEYS = setOf("password", "token", "accessToken", "refreshToken", "apiKey", "secret")
private val SENSITIVE_BEARER_REGEX = Regex("(?i)(Bearer\\s+)([^\\s\"]+)")

internal fun redactSensitiveLogData(raw: String): String {
    var result = raw
    SENSITIVE_QUERY_KEYS.forEach { key ->
        val pattern = Regex("(?i)($key=)([^&\\s]+)")
        result = result.replace(pattern, "$1***")
    }
    SENSITIVE_JSON_KEYS.forEach { key ->
        val pattern = Regex("(?i)(\"$key\"\\s*:\\s*\")(.*?)(\")")
        result = result.replace(pattern, "$1***$3")
    }
    result = result.replace(SENSITIVE_BEARER_REGEX, "$1***")
    return result
}

internal fun createCertificatePinner(
    baseUrl: String,
    environment: String,
): CertificatePinner? {
    if (environment.equals(DEBUG_ENVIRONMENT, ignoreCase = true)) {
        return null
    }
    val host = baseUrl.toHttpUrlOrNull()?.host ?: return null
    if (host !in PINNED_HOSTS) {
        return null
    }
    return CertificatePinner.Builder()
        .add(host, PIN_PRIMARY)
        .add(host, PIN_BACKUP)
        .build()
}

private const val DEBUG_ENVIRONMENT = "debug"
private val PINNED_HOSTS = setOf("api.devpulse.example", "staging-api.devpulse.example")
private const val PIN_PRIMARY = "sha256/afwiKY3RxoMmL1+gD2Q2T6f1V3l0Y7S4A5kZZgwyUrw="
private const val PIN_BACKUP = "sha256/klO23n5h5pLxL7f3vR7Fj1hX1WfNfHwO51j5jC9f4QY="
