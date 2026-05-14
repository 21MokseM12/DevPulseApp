package com.devpulse.app.data.remote

import java.util.Locale

internal const val DEBUG_ENVIRONMENT = "debug"
private const val STAGING_ENVIRONMENT = "staging"
private const val RELEASE_ENVIRONMENT = "release"

internal fun resolveTransportPinsForEnvironment(
    environment: String,
    releasePinsConfig: String,
    stagingPinsConfig: String,
): Set<String> {
    val pinsConfig =
        when (environment.lowercase(Locale.US)) {
            RELEASE_ENVIRONMENT -> releasePinsConfig
            STAGING_ENVIRONMENT -> stagingPinsConfig
            else -> ""
        }
    return pinsConfig
        .split(",")
        .asSequence()
        .map { it.trim() }
        .filter { it.startsWith("sha256/") }
        .filter { it.length > "sha256/".length }
        .toSet()
}
