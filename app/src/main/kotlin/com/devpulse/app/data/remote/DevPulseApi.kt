package com.devpulse.app.data.remote

import retrofit2.http.GET

interface DevPulseApi {
    @GET("actuator/health")
    suspend fun healthCheck(): Map<String, String>
}
