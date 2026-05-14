package com.devpulse.app.ui.settings

import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class QuietHoursPreviewCalculatorTest {
    @Test
    fun calculateQuietHoursPreview_crossMidnight_returnsNearestStartAndEnd() {
        val policy =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 7 * 60,
                weekdays = DayOfWeek.entries.toSet(),
                timezoneMode = QuietHoursTimezoneMode.Fixed,
                fixedZoneId = "UTC",
            )

        val preview =
            calculateQuietHoursPreview(
                policy = policy,
                now = Instant.parse("2026-05-15T12:00:00Z"),
                deviceZoneId = ZoneId.of("Europe/Berlin"),
            )

        assertFalse(preview.isQuietNow)
        assertEquals("UTC", preview.zoneId.id)
        assertEquals("2026-05-15T22:00Z[UTC]", preview.nextStart.toString())
        assertEquals("2026-05-16T07:00Z[UTC]", preview.nextEnd.toString())
    }

    @Test
    fun calculateQuietHoursPreview_whenInsideWindow_returnsCurrentEndAndFutureStart() {
        val policy =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 7 * 60,
                weekdays = DayOfWeek.entries.toSet(),
                timezoneMode = QuietHoursTimezoneMode.Fixed,
                fixedZoneId = "UTC",
            )

        val preview =
            calculateQuietHoursPreview(
                policy = policy,
                now = Instant.parse("2026-05-15T03:00:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertTrue(preview.isQuietNow)
        assertEquals("2026-05-15T22:00Z[UTC]", preview.nextStart.toString())
        assertEquals("2026-05-15T07:00Z[UTC]", preview.nextEnd.toString())
    }

    @Test
    fun formatQuietHoursPreview_usesDeviceTimezoneForDeviceMode() {
        val policy =
            QuietHoursPolicy(
                enabled = true,
                fromMinutes = 22 * 60,
                toMinutes = 6 * 60,
                weekdays = DayOfWeek.entries.toSet(),
                timezoneMode = QuietHoursTimezoneMode.Device,
            )

        val previewText =
            formatQuietHoursPreview(
                policy = policy,
                now = Instant.parse("2026-05-15T19:30:00Z"),
                deviceZoneId = ZoneId.of("Asia/Tokyo"),
            )

        assertTrue(previewText.contains("Часовой пояс: Asia/Tokyo"))
        assertTrue(previewText.contains("Сейчас активны"))
    }

    @Test
    fun calculateQuietHoursPreview_whenDisabled_returnsNoWindow() {
        val preview =
            calculateQuietHoursPreview(
                policy = QuietHoursPolicy(enabled = false),
                now = Instant.parse("2026-05-15T10:00:00Z"),
                deviceZoneId = ZoneId.of("UTC"),
            )

        assertFalse(preview.isQuietNow)
        assertEquals(null, preview.nextStart)
        assertEquals(null, preview.nextEnd)
    }
}
