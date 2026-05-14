package com.devpulse.app.domain.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAcceptanceDeciderTest {
    private val decider = ReleaseAcceptanceDecider()

    @Test
    fun decide_returnsGo_whenRequiredChecksPassedAndNoBlockingDefects() {
        val snapshot =
            ReleaseAcceptanceSnapshot(
                checks =
                    listOf(
                        AcceptanceCheck(id = "AC-01", status = AcceptanceStatus.PASS, requiredForGo = true),
                        AcceptanceCheck(id = "AC-02", status = AcceptanceStatus.PASS, requiredForGo = true),
                        AcceptanceCheck(id = "AC-08", status = AcceptanceStatus.NA, requiredForGo = false),
                    ),
                defects = listOf(ReleaseDefect(DefectSeverity.MINOR)),
            )

        val result = decider.decide(snapshot)

        assertEquals(ReleaseDecision.GO, result.decision)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun decide_returnsNoGo_whenRequiredCheckIsPendingManual() {
        val snapshot =
            ReleaseAcceptanceSnapshot(
                checks =
                    listOf(
                        AcceptanceCheck(id = "AC-01", status = AcceptanceStatus.PENDING_MANUAL, requiredForGo = true),
                        AcceptanceCheck(id = "AC-02", status = AcceptanceStatus.PASS, requiredForGo = true),
                    ),
                defects = emptyList(),
            )

        val result = decider.decide(snapshot)

        assertEquals(ReleaseDecision.NO_GO, result.decision)
        assertEquals(listOf("required_checks_not_passed=AC-01"), result.reasons)
    }

    @Test
    fun decide_returnsNoGo_whenMajorDefectExists() {
        val snapshot =
            ReleaseAcceptanceSnapshot(
                checks =
                    listOf(
                        AcceptanceCheck(id = "AC-01", status = AcceptanceStatus.PASS, requiredForGo = true),
                        AcceptanceCheck(id = "AC-02", status = AcceptanceStatus.PASS, requiredForGo = true),
                    ),
                defects = listOf(ReleaseDefect(DefectSeverity.MAJOR)),
            )

        val result = decider.decide(snapshot)

        assertEquals(ReleaseDecision.NO_GO, result.decision)
        assertEquals(listOf("blocking_or_major_defects_present"), result.reasons)
    }
}
