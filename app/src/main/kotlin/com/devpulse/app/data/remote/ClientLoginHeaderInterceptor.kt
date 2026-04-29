package com.devpulse.app.data.remote

import com.devpulse.app.data.local.preferences.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientLoginHeaderInterceptor : Interceptor {
    private val sessionStore: SessionStore

    @Inject
    constructor(sessionStore: SessionStore) {
        this.sessionStore = sessionStore
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/api/v1/links")) {
            return chain.proceed(request)
        }

        val login = runBlocking { sessionStore.getSession()?.login }
        if (login.isNullOrBlank()) {
            return chain.proceed(request)
        }

        val updatedRequest =
            request.newBuilder()
                .header(CLIENT_LOGIN_HEADER, login)
                .build()
        return chain.proceed(updatedRequest)
    }

    private companion object {
        const val CLIENT_LOGIN_HEADER = "Client-Login"
    }
}
