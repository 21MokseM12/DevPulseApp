package com.devpulse.app.data.remote

import com.devpulse.app.domain.model.ApiError
import com.devpulse.app.domain.model.ApiErrorKind
import java.util.Locale

internal object AuthFallbackPolicy {
    private val loginEndpointUnavailableStatuses = setOf(404, 405, 501)
    private val endpointUnavailableMarkers =
        listOf(
            "not found",
            "no static resource",
            "method not allowed",
            "unsupported",
            "unknown endpoint",
            "not implemented",
        )
    private val alreadyExistsMarkers =
        listOf(
            "already exists",
            "уже существует",
            "already registered",
            "already created",
            "duplicate",
            "conflict",
        )

    fun shouldFallbackToRegister(
        statusCode: Int,
        error: ApiError,
    ): Boolean {
        if (statusCode in loginEndpointUnavailableStatuses) {
            return true
        }
        if (statusCode != 400) {
            return false
        }
        if (error.kind != ApiErrorKind.BadRequest) {
            return false
        }
        return containsAnyMarker(error, endpointUnavailableMarkers)
    }

    fun shouldTreatRegisterConflictAsLoginSuccess(error: ApiError): Boolean {
        return error.kind == ApiErrorKind.BadRequest && containsAnyMarker(error, alreadyExistsMarkers)
    }

    private fun containsAnyMarker(
        error: ApiError,
        markers: List<String>,
    ): Boolean {
        val normalizedMessage = error.userMessage.lowercase(Locale.ROOT)
        val normalizedCode = error.code?.lowercase(Locale.ROOT).orEmpty()
        val normalizedTechnicalDescription = error.technicalDescription?.lowercase(Locale.ROOT).orEmpty()
        return markers.any { marker ->
            normalizedMessage.contains(marker) ||
                normalizedCode.contains(marker) ||
                normalizedTechnicalDescription.contains(marker)
        }
    }
}
