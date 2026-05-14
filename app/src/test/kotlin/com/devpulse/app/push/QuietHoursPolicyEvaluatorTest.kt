package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class QuietHoursPolicyEvaluatorTest {
    private val evaluator = QuietHoursPolicyEvaluator()
    private val regularUpdate =
        ParsedPushUpdate(
            remoteEventId = "evt-1",
            linkUpdateId = 1L,
            updateOwner = "bot",
            creationDate = "",
            linkUrl = "https://example.com",
            title = "Title",
            content = "Body",
            isCritical = false,
        )

    @Test
    fun shouldSuppressNotification_crossMidnightWindow_suppressesInsideWindow() {
        val schedule =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 7 * 60,
                weekdays = DayOfWeek.entries.toSet(),
            )

        val suppressed =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = regularUpdate,
                now = Instant.parse("2026-05-15T03:00:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertTrue(suppressed)
    }

    @Test
    fun shouldSuppressNotification_crossMidnightWindow_allowsOutsideWindow() {
        val schedule =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 7 * 60,
                weekdays = DayOfWeek.entries.toSet(),
            )

        val suppressed =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = regularUpdate,
                now = Instant.parse("2026-05-15T12:00:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertFalse(suppressed)
    }

    @Test
    fun shouldSuppressNotification_criticalUpdate_overridesQuietHours() {
        val schedule = QuietHoursPolicy(enabled = true)
        val criticalUpdate = regularUpdate.copy(isCritical = true)

        val suppressed =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = criticalUpdate,
                now = Instant.parse("2026-05-15T01:00:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertFalse(suppressed)
    }

    @Test
    fun shouldSuppressNotification_fixedTimezone_usesConfiguredZone() {
        val schedule =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 6 * 60,
                weekdays = DayOfWeek.entries.toSet(),
                timezoneMode = QuietHoursTimezoneMode.Fixed,
                fixedZoneId = "Asia/Tokyo",
            )
        val now = Instant.parse("2026-05-15T13:30:00Z")

        val suppressedWithUtcDeviceZone =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = regularUpdate,
                now = now,
                deviceZoneId = ZoneId.of("UTC"),
            )
        val suppressedWithBerlinDeviceZone =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = regularUpdate,
                now = now,
                deviceZoneId = ZoneId.of("Europe/Berlin"),
            )

        assertTrue(suppressedWithUtcDeviceZone)
        assertTrue(suppressedWithBerlinDeviceZone)
    }

    @Test
    fun shouldSuppressNotification_dstTransition_evaluatesWithLocalClock() {
        val schedule =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 60,
                toMinutes = 4 * 60,
                weekdays = setOf(DayOfWeek.SUNDAY),
                timezoneMode = QuietHoursTimezoneMode.Fixed,
                fixedZoneId = "Europe/Berlin",
            )

        val suppressed =
            evaluator.shouldSuppressNotification(
                schedule = schedule,
                update = regularUpdate,
                now = Instant.parse("2026-03-29T01:30:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertTrue(suppressed)
    }
}
