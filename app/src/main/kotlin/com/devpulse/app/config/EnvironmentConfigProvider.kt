package com.devpulse.app.config

import com.devpulse.app.BuildConfig
import javax.inject.Inject

data class EnvironmentConfig(
    val name: String,
    val baseUrl: String,
)

class EnvironmentConfigProvider
    @Inject
    constructor() {
        fun get(): EnvironmentConfig {
            return EnvironmentConfig(
                name = BuildConfig.ENVIRONMENT,
                baseUrl = BuildConfig.BASE_URL,
            )
        }
    }
