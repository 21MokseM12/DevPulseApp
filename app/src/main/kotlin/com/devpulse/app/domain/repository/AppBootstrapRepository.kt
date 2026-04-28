package com.devpulse.app.domain.repository

data class AppBootstrapInfo(
    val environment: String,
    val baseUrl: String,
    val hasCachedSession: Boolean,
)

interface AppBootstrapRepository {
    suspend fun loadBootstrapInfo(): AppBootstrapInfo
}
