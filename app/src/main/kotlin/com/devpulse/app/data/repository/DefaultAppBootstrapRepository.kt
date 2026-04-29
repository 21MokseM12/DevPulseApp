package com.devpulse.app.data.repository

import com.devpulse.app.config.EnvironmentConfigProvider
import com.devpulse.app.data.local.preferences.SessionStore
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppBootstrapRepository
    @Inject
    constructor(
        private val configProvider: EnvironmentConfigProvider,
        private val sessionStore: SessionStore,
    ) : AppBootstrapRepository {
        override suspend fun loadBootstrapInfo(): AppBootstrapInfo {
            val config = configProvider.get()
            val hasSession = sessionStore.getSession() != null

            return AppBootstrapInfo(
                environment = config.name,
                baseUrl = config.baseUrl,
                hasCachedSession = hasSession,
            )
        }
    }
