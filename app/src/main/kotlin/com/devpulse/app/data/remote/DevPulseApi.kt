package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST

interface DevPulseApi {
    @POST("/api/v1/clients")
    suspend fun registerClient(
        @Body request: ClientCredentialsRequestDto,
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "/api/v1/clients", hasBody = true)
    suspend fun unregisterClient(
        @Body request: ClientCredentialsRequestDto,
    ): Response<Unit>

    @GET("/api/v1/links")
    suspend fun getLinks(): Response<List<LinkResponseDto>>

    @POST("/api/v1/links")
    suspend fun addLink(
        @Body request: AddLinkRequestDto,
    ): Response<LinkResponseDto>

    @HTTP(method = "DELETE", path = "/api/v1/links", hasBody = true)
    suspend fun removeLink(
        @Body request: RemoveLinkRequestDto,
    ): Response<LinkResponseDto>
}
