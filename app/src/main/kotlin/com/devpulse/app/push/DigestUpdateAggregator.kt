package com.devpulse.app.push

import com.devpulse.app.domain.model.UpdateEvent
import javax.inject.Inject
import javax.inject.Singleton

data class DigestSummaryPayload(
    val updatesCount: Int,
    val periodStartEpochMs: Long,
    val periodEndEpochMs: Long,
    val sourceBreakdown: Map<String, Int>,
)

@Singleton
class DigestUpdateAggregator
    @Inject
    constructor() {
        fun aggregate(
            updates: List<UpdateEvent>,
            periodStartExclusiveEpochMs: Long,
            periodEndInclusiveEpochMs: Long,
        ): DigestSummaryPayload? {
            val windowed =
                updates
                    .filter { update ->
                        update.receivedAtEpochMs > periodStartExclusiveEpochMs &&
                            update.receivedAtEpochMs <= periodEndInclusiveEpochMs
                    }.distinctBy { it.id }
            if (windowed.isEmpty()) return null

            val sourceBreakdown =
                windowed
                    .groupingBy { update ->
                        update.source.trim().ifBlank { "unknown" }
                    }.eachCount()
                    .toSortedMap()

            return DigestSummaryPayload(
                updatesCount = windowed.size,
                periodStartEpochMs = periodStartExclusiveEpochMs,
                periodEndEpochMs = periodEndInclusiveEpochMs,
                sourceBreakdown = sourceBreakdown,
            )
        }
    }
