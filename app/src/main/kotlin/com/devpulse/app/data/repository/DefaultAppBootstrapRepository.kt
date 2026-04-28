package com.devpulse.app.data.repository

import com.devpulse.app.config.EnvironmentConfigProvider
import com.devpulse.app.data.local.db.SessionDao
import com.devpulse.app.data.local.preferences.UserSessionPreferences
import com.devpulse.app.domain.repository.AppBootstrapInfo
import com.devpulse.app.domain.repository.AppBootstrapRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DefaultAppBootstrapRepository @Inject constructor(
    private val configProvider: EnvironmentConfigProvider,
    private val sessionDao: SessionDao,
    private val userSessionPreferences: UserSessionPreferences,
) : AppBootstrapRepository {
    override suspend fun loadBootstrapInfo(): AppBootstrapInfo {
        val config = configProvider.get()
        val hasCachedDbSession = sessionDao.getSession() != null
        val hasCachedPrefSession = !userSessionPreferences.clientLogin.first().isNullOrBlank()

        return AppBootstrapInfo(
            environment = config.name,
            baseUrl = config.baseUrl,
            hasCachedSession = hasCachedDbSession || hasCachedPrefSession,
        )
    }
}
