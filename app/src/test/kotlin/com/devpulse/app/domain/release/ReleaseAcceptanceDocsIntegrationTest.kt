package com.devpulse.app.domain.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseAcceptanceDocsIntegrationTest {
    private val decider = ReleaseAcceptanceDecider()

    @Test
    fun releaseDocs_currentChecklistStateMatchesNoGoDecision() {
        val root = repositoryRoot()
        val checklistFile = root.resolve("release/android-mvp-acceptance-checklist.md")
        val goNoGoFile = root.resolve("release/android-mvp-go-no-go.md")

        assertTrue("Checklist file is missing", checklistFile.exists())
        assertTrue("Go/No-Go file is missing", goNoGoFile.exists())

        val checklistRows = parseChecklistRows(checklistFile.readText())
        val requiredChecks = (1..7).map { index -> "AC-${index.toString().padStart(2, '0')}" }
        requiredChecks.forEach { checkId ->
            assertTrue("Missing required checklist row: $checkId", checklistRows.containsKey(checkId))
        }

        val snapshot =
            ReleaseAcceptanceSnapshot(
                checks =
                    checklistRows.entries.map { (id, status) ->
                        AcceptanceCheck(
                            id = id,
                            status = status,
                            requiredForGo = requiredChecks.contains(id),
                        )
                    },
                defects = emptyList(),
            )

        val decision = decider.decide(snapshot)
        assertEquals(ReleaseDecision.NO_GO, decision.decision)
        assertTrue(decision.reasons.any { it.startsWith("required_checks_not_passed=") })

        val goNoGoText = goNoGoFile.readText()
        assertTrue("Go/No-Go document is expected to state NO-GO", goNoGoText.contains("Решение: `NO-GO`"))
    }

    private fun repositoryRoot(): File {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        if (currentDir.resolve("BACKLOG.md").exists()) {
            return currentDir
        }
        val parent = currentDir.parentFile ?: return currentDir
        return if (parent.resolve("BACKLOG.md").exists()) parent else currentDir
    }

    private fun parseChecklistRows(markdown: String): Map<String, AcceptanceStatus> {
        val rowRegex =
            Regex(
                """\|\s*(AC-\d+)\s*\|.*\|\s*(PASS|FAIL|PENDING_MANUAL|N/A)\s*\|""",
            )

        return rowRegex.findAll(markdown).associate { match ->
            val checkId = match.groupValues[1]
            val rawStatus = match.groupValues[2]
            checkId to statusFrom(rawStatus)
        }
    }

    private fun statusFrom(rawStatus: String): AcceptanceStatus {
        return when (rawStatus) {
            "PASS" -> AcceptanceStatus.PASS
            "FAIL" -> AcceptanceStatus.FAIL
            "PENDING_MANUAL" -> AcceptanceStatus.PENDING_MANUAL
            "N/A" -> AcceptanceStatus.NA
            else -> error("Unknown status: $rawStatus")
        }
    }
}
