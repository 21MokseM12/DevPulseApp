package com.devpulse.app.data.remote

import com.devpulse.app.data.remote.dto.AddLinkRequestDto
import com.devpulse.app.data.remote.dto.BotApiMessageResponseDto
import com.devpulse.app.data.remote.dto.ClientCredentialsRequestDto
import com.devpulse.app.data.remote.dto.LinkResponseDto
import com.devpulse.app.data.remote.dto.MarkReadRequestDto
import com.devpulse.app.data.remote.dto.MarkReadResponseDto
import com.devpulse.app.data.remote.dto.NotificationListResponseDto
import com.devpulse.app.data.remote.dto.RemoveLinkRequestDto
import com.devpulse.app.data.remote.dto.UnreadCountResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query

interface DevPulseApi {
    @POST("/api/v1/clients/login")
    suspend fun loginClient(
        @Body request: ClientCredentialsRequestDto,
    ): Response<Unit>

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
    ): Response<BotApiMessageResponseDto>

    @GET("/api/v1/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("tags") tags: List<String>,
    ): Response<NotificationListResponseDto>

    @GET("/api/v1/notifications/unread-count")
    suspend fun getUnreadNotificationsCount(): Response<UnreadCountResponseDto>

    @POST("/api/v1/notifications/mark-read")
    suspend fun markNotificationsRead(
        @Body request: MarkReadRequestDto,
    ): Response<MarkReadResponseDto>
}
