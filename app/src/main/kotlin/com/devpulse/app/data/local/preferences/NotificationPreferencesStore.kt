package com.devpulse.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationPresentationMode {
    Compact,
    Detailed,
}

enum class NotificationDigestMode {
    Daily,
}

enum class QuietHoursTimezoneMode {
    Device,
    Fixed,
}

data class QuietHoursPolicy(
    val enabled: Boolean = false,
    val fromMinutes: Int = DEFAULT_FROM_MINUTES,
    val toMinutes: Int = DEFAULT_TO_MINUTES,
    val weekdays: Set<DayOfWeek> = DEFAULT_WEEKDAYS,
    val timezoneMode: QuietHoursTimezoneMode = QuietHoursTimezoneMode.Device,
    val fixedZoneId: String? = null,
) {
    val resolvedFixedZoneId: String?
        get() {
            val value = fixedZoneId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { ZoneId.of(value) }.getOrNull()?.id
        }

    companion object {
        const val DEFAULT_FROM_MINUTES: Int = 22 * 60
        const val DEFAULT_TO_MINUTES: Int = 7 * 60
        val DEFAULT_WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

data class NotificationPreferences(
    val enabled: Boolean = true,
    val presentationMode: NotificationPresentationMode = NotificationPresentationMode.Detailed,
    val digestMode: NotificationDigestMode? = null,
    val quietHoursPolicy: QuietHoursPolicy = QuietHoursPolicy(),
)

interface NotificationPreferencesStore {
    fun observePreferences(): Flow<NotificationPreferences>

    suspend fun getPreferences(): NotificationPreferences

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setPresentationMode(mode: NotificationPresentationMode)

    suspend fun setDigestMode(mode: NotificationDigestMode?)

    suspend fun setQuietHoursPolicy(policy: QuietHoursPolicy)

    suspend fun reset()
}

@Singleton
class DataStoreNotificationPreferencesStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : NotificationPreferencesStore {
        @Volatile
        private var inMemoryFallback: NotificationPreferences? = null

        override fun observePreferences(): Flow<NotificationPreferences> {
            return dataStore.data
                .catch { error ->
                    emit(emptyPreferences())
                }.map { preferences ->
                    inMemoryFallback ?: parsePreferences(preferences)
                }
        }

        override suspend fun getPreferences(): NotificationPreferences = observePreferences().first()

        override suspend fun setEnabled(enabled: Boolean) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(enabled = enabled)
            runCatching {
                dataStore.edit { preferences ->
                    preferences[ENABLED_KEY] = enabled
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun setPresentationMode(mode: NotificationPresentationMode) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(presentationMode = mode)
            runCatching {
                dataStore.edit { preferences ->
                    preferences[PRESENTATION_MODE_KEY] = mode.name
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun setDigestMode(mode: NotificationDigestMode?) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(digestMode = mode)
            runCatching {
                dataStore.edit { preferences ->
                    if (mode == null) {
                        preferences.remove(DIGEST_MODE_KEY)
                    } else {
                        preferences[DIGEST_MODE_KEY] = mode.name
                    }
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun setQuietHoursPolicy(policy: QuietHoursPolicy) {
            val current = runCatching { getPreferences() }.getOrDefault(NotificationPreferences())
            val next = current.copy(quietHoursPolicy = normalizePolicy(policy))
            runCatching {
                dataStore.edit { preferences ->
                    val normalized = normalizePolicy(policy)
                    preferences[QUIET_HOURS_ENABLED_KEY] = normalized.enabled
                    preferences[QUIET_HOURS_FROM_KEY] = normalized.fromMinutes
                    preferences[QUIET_HOURS_TO_KEY] = normalized.toMinutes
                    preferences[QUIET_HOURS_WEEKDAYS_KEY] = serializeWeekdays(normalized.weekdays)
                    preferences[QUIET_HOURS_TIMEZONE_MODE_KEY] = normalized.timezoneMode.name
                    val fixedZoneId = normalized.resolvedFixedZoneId
                    if (fixedZoneId == null) {
                        preferences.remove(QUIET_HOURS_FIXED_ZONE_ID_KEY)
                    } else {
                        preferences[QUIET_HOURS_FIXED_ZONE_ID_KEY] = fixedZoneId
                    }
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = next
            }
        }

        override suspend fun reset() {
            runCatching {
                dataStore.edit { preferences ->
                    preferences.remove(ENABLED_KEY)
                    preferences.remove(PRESENTATION_MODE_KEY)
                    preferences.remove(DIGEST_MODE_KEY)
                    preferences.remove(QUIET_HOURS_ENABLED_KEY)
                    preferences.remove(QUIET_HOURS_FROM_KEY)
                    preferences.remove(QUIET_HOURS_TO_KEY)
                    preferences.remove(QUIET_HOURS_WEEKDAYS_KEY)
                    preferences.remove(QUIET_HOURS_TIMEZONE_MODE_KEY)
                    preferences.remove(QUIET_HOURS_FIXED_ZONE_ID_KEY)
                }
                inMemoryFallback = null
            }.onFailure { error ->
                inMemoryFallback = NotificationPreferences()
            }
        }

        private fun parsePreferences(preferences: Preferences): NotificationPreferences {
            return NotificationPreferences(
                enabled = preferences[ENABLED_KEY] ?: true,
                presentationMode = parseMode(preferences[PRESENTATION_MODE_KEY]),
                digestMode = parseDigestMode(preferences[DIGEST_MODE_KEY]),
                quietHoursPolicy =
                    parseQuietHoursPolicy(
                        enabled = preferences[QUIET_HOURS_ENABLED_KEY],
                        fromMinutes = preferences[QUIET_HOURS_FROM_KEY],
                        toMinutes = preferences[QUIET_HOURS_TO_KEY],
                        weekdaysRaw = preferences[QUIET_HOURS_WEEKDAYS_KEY],
                        timezoneModeRaw = preferences[QUIET_HOURS_TIMEZONE_MODE_KEY],
                        fixedZoneIdRaw = preferences[QUIET_HOURS_FIXED_ZONE_ID_KEY],
                    ),
            )
        }

        private fun parseMode(rawValue: String?): NotificationPresentationMode {
            return NotificationPresentationMode.entries.firstOrNull { it.name == rawValue }
                ?: NotificationPresentationMode.Detailed
        }

        private fun parseDigestMode(rawValue: String?): NotificationDigestMode? {
            return NotificationDigestMode.entries.firstOrNull { it.name == rawValue }
        }

        private fun parseQuietHoursPolicy(
            enabled: Boolean?,
            fromMinutes: Int?,
            toMinutes: Int?,
            weekdaysRaw: String?,
            timezoneModeRaw: String?,
            fixedZoneIdRaw: String?,
        ): QuietHoursPolicy {
            val timezoneMode =
                QuietHoursTimezoneMode.entries.firstOrNull { it.name == timezoneModeRaw }
                    ?: QuietHoursTimezoneMode.Device
            return normalizePolicy(
                QuietHoursPolicy(
                    enabled = enabled ?: false,
                    fromMinutes = fromMinutes ?: QuietHoursPolicy.DEFAULT_FROM_MINUTES,
                    toMinutes = toMinutes ?: QuietHoursPolicy.DEFAULT_TO_MINUTES,
                    weekdays = parseWeekdays(weekdaysRaw),
                    timezoneMode = timezoneMode,
                    fixedZoneId = fixedZoneIdRaw,
                ),
            )
        }

        private fun parseWeekdays(rawValue: String?): Set<DayOfWeek> {
            if (rawValue.isNullOrBlank()) return QuietHoursPolicy.DEFAULT_WEEKDAYS
            val parsed =
                rawValue
                    .split(",")
                    .mapNotNull { token -> DayOfWeek.entries.firstOrNull { it.name == token.trim() } }
                    .toSet()
            return parsed.ifEmpty { QuietHoursPolicy.DEFAULT_WEEKDAYS }
        }

        private fun serializeWeekdays(weekdays: Set<DayOfWeek>): String {
            return weekdays
                .sortedBy { it.value }
                .joinToString(separator = ",") { it.name }
        }

        private fun normalizePolicy(policy: QuietHoursPolicy): QuietHoursPolicy {
            val normalizedWeekdays = policy.weekdays.ifEmpty { QuietHoursPolicy.DEFAULT_WEEKDAYS }
            val normalizedMode = policy.timezoneMode
            val normalizedFixedZoneId =
                if (normalizedMode == QuietHoursTimezoneMode.Fixed) {
                    policy.resolvedFixedZoneId ?: ZoneId.systemDefault().id
                } else {
                    null
                }
            return policy.copy(
                fromMinutes = normalizeMinutes(policy.fromMinutes),
                toMinutes = normalizeMinutes(policy.toMinutes),
                weekdays = normalizedWeekdays,
                timezoneMode = normalizedMode,
                fixedZoneId = normalizedFixedZoneId,
            )
        }

        private fun normalizeMinutes(value: Int): Int {
            return value.coerceIn(0, MINUTES_PER_DAY - 1)
        }

        private companion object {
            const val MINUTES_PER_DAY: Int = 24 * 60
            private val ENABLED_KEY = booleanPreferencesKey("notification_preferences_enabled")
            private val PRESENTATION_MODE_KEY = stringPreferencesKey("notification_preferences_presentation_mode")
            private val DIGEST_MODE_KEY = stringPreferencesKey("notification_preferences_digest_mode")
            private val QUIET_HOURS_ENABLED_KEY = booleanPreferencesKey("notification_preferences_quiet_enabled")
            private val QUIET_HOURS_FROM_KEY =
                intPreferencesKey(
                    "notification_preferences_quiet_from_minutes",
                )
            private val QUIET_HOURS_TO_KEY =
                intPreferencesKey(
                    "notification_preferences_quiet_to_minutes",
                )
            private val QUIET_HOURS_WEEKDAYS_KEY = stringPreferencesKey("notification_preferences_quiet_weekdays")
            private val QUIET_HOURS_TIMEZONE_MODE_KEY =
                stringPreferencesKey("notification_preferences_quiet_timezone_mode")
            private val QUIET_HOURS_FIXED_ZONE_ID_KEY =
                stringPreferencesKey("notification_preferences_quiet_fixed_zone_id")
        }
    }
