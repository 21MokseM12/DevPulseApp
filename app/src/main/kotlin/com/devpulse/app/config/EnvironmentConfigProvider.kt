package com.devpulse.app.config

import com.devpulse.app.BuildConfig

data class EnvironmentConfig(
    val name: String,
    val baseUrl: String,
)

class EnvironmentConfigProvider {
    fun get(): EnvironmentConfig {
        return EnvironmentConfig(
            name = BuildConfig.ENVIRONMENT,
            baseUrl = BuildConfig.BASE_URL,
        )
    }
}
