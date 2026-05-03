package com.devpulse.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionEvaluatorTest {
    private val evaluator = NotificationPermissionEvaluator()

    @Test
    fun resolve_sdkBelow33_returnsNotRequired() {
        val result =
            evaluator.resolve(
                sdkInt = 32,
                hasRuntimePermission = false,
                notificationsEnabled = true,
                hasRequestedBefore = false,
                shouldShowRationale = false,
            )

        assertEquals(NotificationPermissionState.NotRequired, result)
    }

    @Test
    fun resolve_grantedAndEnabled_returnsGranted() {
        val result =
            evaluator.resolve(
                sdkInt = 33,
                hasRuntimePermission = true,
                notificationsEnabled = true,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            )

        assertEquals(NotificationPermissionState.Granted, result)
    }

    @Test
    fun resolve_neverRequestedWithoutPermission_returnsNeedsRequest() {
        val result =
            evaluator.resolve(
                sdkInt = 34,
                hasRuntimePermission = false,
                notificationsEnabled = true,
                hasRequestedBefore = false,
                shouldShowRationale = false,
            )

        assertEquals(NotificationPermissionState.NeedsRequest, result)
    }

    @Test
    fun resolve_requestedAndNoRationale_returnsNeedsSettings() {
        val result =
            evaluator.resolve(
                sdkInt = 34,
                hasRuntimePermission = false,
                notificationsEnabled = false,
                hasRequestedBefore = true,
                shouldShowRationale = false,
            )

        assertEquals(NotificationPermissionState.NeedsSettings, result)
    }
}
