package com.devpulse.app.ui.settings

import com.devpulse.app.data.local.preferences.QuietHoursPolicy
import com.devpulse.app.data.local.preferences.QuietHoursTimezoneMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal data class QuietHoursPreview(
    val zoneId: ZoneId,
    val isQuietNow: Boolean,
    val nextStart: ZonedDateTime?,
    val nextEnd: ZonedDateTime?,
)

private data class QuietInterval(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

internal fun calculateQuietHoursPreview(
    policy: QuietHoursPolicy,
    now: Instant,
    deviceZoneId: ZoneId = ZoneId.systemDefault(),
): QuietHoursPreview {
    val resolvedZoneId =
        if (policy.timezoneMode == QuietHoursTimezoneMode.Device) {
            deviceZoneId
        } else {
            policy.resolvedFixedZoneId?.let(ZoneId::of) ?: deviceZoneId
        }

    if (!policy.enabled || policy.weekdays.isEmpty()) {
        return QuietHoursPreview(
            zoneId = resolvedZoneId,
            isQuietNow = false,
            nextStart = null,
            nextEnd = null,
        )
    }

    val zonedNow = now.atZone(resolvedZoneId)
    val intervals = buildMergedIntervals(policy, zonedNow.toLocalDate(), resolvedZoneId)
    val activeInterval = intervals.firstOrNull { !zonedNow.isBefore(it.start) && zonedNow.isBefore(it.end) }
    val upcomingStartInterval = intervals.firstOrNull { !it.start.isBefore(zonedNow) }

    return QuietHoursPreview(
        zoneId = resolvedZoneId,
        isQuietNow = activeInterval != null,
        nextStart = upcomingStartInterval?.start,
        nextEnd = activeInterval?.end ?: upcomingStartInterval?.end,
    )
}

internal fun formatQuietHoursPreview(
    policy: QuietHoursPolicy,
    now: Instant,
    deviceZoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (!policy.enabled) return "Quiet hours выключены."

    val preview = calculateQuietHoursPreview(policy, now, deviceZoneId)
    val zoneId = preview.zoneId.id
    val formatter = DateTimeFormatter.ofPattern("EEE, dd.MM HH:mm")
    val weekdays =
        policy.weekdays
            .sortedBy { it.value }
            .joinToString(", ") { it.shortLabel() }

    if (preview.nextEnd == null && preview.nextStart == null) {
        return "Quiet hours включены, но не удалось вычислить ближайшее окно ($zoneId). Дни: $weekdays."
    }

    val status = if (preview.isQuietNow) "Сейчас активны" else "Сейчас не активны"
    val startLabel = if (preview.isQuietNow) "Следующий старт" else "Ближайший старт"
    val endLabel = if (preview.isQuietNow) "Ближайшее завершение" else "Ближайшее завершение"
    val startText = preview.nextStart?.format(formatter) ?: "не найден"
    val endText = preview.nextEnd?.format(formatter) ?: "не найден"

    return buildString {
        append("Quiet hours: ")
        append(status)
        append(".\n")
        append(startLabel)
        append(": ")
        append(startText)
        append(".\n")
        append(endLabel)
        append(": ")
        append(endText)
        append(".\n")
        append("Часовой пояс: ")
        append(zoneId)
        append(". Дни: ")
        append(weekdays)
        append(".")
    }
}

private fun buildMergedIntervals(
    policy: QuietHoursPolicy,
    referenceDate: LocalDate,
    zoneId: ZoneId,
): List<QuietInterval> {
    val rawIntervals =
        (-1L..8L)
            .mapNotNull { offset ->
                buildIntervalForDate(
                    policy = policy,
                    date = referenceDate.plusDays(offset),
                    zoneId = zoneId,
                )
            }
            .sortedBy(QuietInterval::start)

    if (rawIntervals.isEmpty()) return emptyList()

    val merged = mutableListOf(rawIntervals.first())
    for (interval in rawIntervals.drop(1)) {
        val previous = merged.last()
        if (!interval.start.isAfter(previous.end)) {
            merged[merged.lastIndex] =
                previous.copy(
                    end = maxOf(previous.end, interval.end),
                )
        } else {
            merged += interval
        }
    }
    return merged
}

private fun buildIntervalForDate(
    policy: QuietHoursPolicy,
    date: LocalDate,
    zoneId: ZoneId,
): QuietInterval? {
    if (!policy.weekdays.contains(date.dayOfWeek)) return null

    val start = date.atTime(policy.fromMinutes / 60, policy.fromMinutes % 60).atZone(zoneId)
    val endDate =
        when {
            policy.fromMinutes < policy.toMinutes -> date
            else -> date.plusDays(1)
        }
    val end = endDate.atTime(policy.toMinutes / 60, policy.toMinutes % 60).atZone(zoneId)
    return QuietInterval(start = start, end = end)
}

private fun DayOfWeek.shortLabel(): String {
    return getDisplayName(TextStyle.SHORT, Locale("ru"))
}
