package com.devpulse.app.push

import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuietHoursPolicyEvaluator
    @Inject
    constructor() {
        fun shouldSuppressNotification(
            schedule: QuietHoursPolicy,
            update: ParsedPushUpdate,
            now: Instant,
            deviceZoneId: ZoneId = ZoneId.systemDefault(),
        ): Boolean {
            if (!schedule.enabled) return false
            if (update.isCritical) return false
            return isQuietNow(
                schedule = schedule,
                now = now,
                deviceZoneId = deviceZoneId,
            )
        }

        fun isQuietNow(
            schedule: QuietHoursPolicy,
            now: Instant,
            deviceZoneId: ZoneId = ZoneId.systemDefault(),
        ): Boolean {
            if (!schedule.enabled) return false
            val zoneId = resolveZoneId(schedule, deviceZoneId)
            val zonedNow = now.atZone(zoneId)
            return isWithinQuietWindow(
                day = zonedNow.dayOfWeek,
                minuteOfDay = zonedNow.toMinuteOfDay(),
                fromMinutes = schedule.fromMinutes,
                toMinutes = schedule.toMinutes,
                weekdays = schedule.weekdays,
            )
        }

        private fun resolveZoneId(
            schedule: QuietHoursPolicy,
            deviceZoneId: ZoneId,
        ): ZoneId {
            if (schedule.timezoneMode == QuietHoursTimezoneMode.Device) return deviceZoneId
            return schedule.resolvedFixedZoneId?.let { ZoneId.of(it) } ?: deviceZoneId
        }
    }

internal fun isWithinQuietWindow(
    day: DayOfWeek,
    minuteOfDay: Int,
    fromMinutes: Int,
    toMinutes: Int,
    weekdays: Set<DayOfWeek>,
): Boolean {
    if (weekdays.isEmpty()) return false
    if (fromMinutes == toMinutes) return weekdays.contains(day)
    if (fromMinutes < toMinutes) {
        return weekdays.contains(day) && minuteOfDay in fromMinutes until toMinutes
    }
    val previousDay = day.minus(1)
    val inLateWindow = weekdays.contains(day) && minuteOfDay >= fromMinutes
    val inEarlyWindow = weekdays.contains(previousDay) && minuteOfDay < toMinutes
    return inLateWindow || inEarlyWindow
}

internal fun ZonedDateTime.toMinuteOfDay(): Int = hour * 60 + minute
