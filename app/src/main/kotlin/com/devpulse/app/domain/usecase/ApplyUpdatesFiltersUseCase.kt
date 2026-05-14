package com.devpulse.app.domain.usecase

import com.devpulse.app.domain.model.UpdateEvent
import com.devpulse.app.domain.model.UpdatesFilterState
import com.devpulse.app.domain.model.UpdatesPeriodFilter
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.max

class ApplyUpdatesFiltersUseCase
    @Inject
    constructor() {
        operator fun invoke(
            events: List<UpdateEvent>,
            state: UpdatesFilterState,
            nowEpochMs: Long = System.currentTimeMillis(),
        ): List<UpdateEvent> {
            val normalizedQuery = state.query.trim().lowercase()
            val nowInstant = Instant.ofEpochMilli(nowEpochMs)
            val startOfTodayEpochMs =
                nowInstant
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
            val periodStartEpochMs =
                when (state.period) {
                    UpdatesPeriodFilter.ALL -> Long.MIN_VALUE
                    UpdatesPeriodFilter.TODAY -> startOfTodayEpochMs
                    UpdatesPeriodFilter.LAST_7_DAYS -> max(0L, startOfTodayEpochMs - DAYS_7_MS)
                    UpdatesPeriodFilter.LAST_30_DAYS -> max(0L, startOfTodayEpochMs - DAYS_30_MS)
                }
            val effectivePeriodStartEpochMs = max(periodStartEpochMs, state.periodStartEpochMs ?: Long.MIN_VALUE)
            val effectivePeriodEndEpochMs = state.periodEndEpochMs ?: Long.MAX_VALUE
            val selectedTags = state.selectedTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val normalizedSource = state.source?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

            return events.filter { event ->
                if (state.unreadOnly && event.isRead) return@filter false

                if (normalizedQuery.isNotBlank()) {
                    val sourceText = event.source.lowercase()
                    val linkText = event.linkUrl.lowercase()
                    val titleText = event.title.lowercase()
                    val contentText = event.content.lowercase()
                    if (
                        !titleText.contains(normalizedQuery) &&
                        !contentText.contains(normalizedQuery) &&
                        !linkText.contains(normalizedQuery) &&
                        !sourceText.contains(normalizedQuery)
                    ) {
                        return@filter false
                    }
                }

                if (normalizedSource != null) {
                    if (event.source.trim().lowercase() != normalizedSource) {
                        return@filter false
                    }
                }

                if (event.receivedAtEpochMs < effectivePeriodStartEpochMs) {
                    return@filter false
                }
                if (event.receivedAtEpochMs > effectivePeriodEndEpochMs) {
                    return@filter false
                }

                if (selectedTags.isNotEmpty()) {
                    val eventTags = event.tags.map { it.trim().lowercase() }.toSet()
                    if (!selectedTags.all { it in eventTags }) return@filter false
                }

                true
            }
        }

        private companion object {
            const val DAYS_7_MS = 7L * 24L * 60L * 60L * 1000L
            const val DAYS_30_MS = 30L * 24L * 60L * 60L * 1000L
        }
    }
