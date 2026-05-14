package com.devpulse.app.domain.release

enum class AcceptanceStatus {
    PASS,
    FAIL,
    PENDING_MANUAL,
    NA,
}

enum class DefectSeverity {
    BLOCKER,
    MAJOR,
    MINOR,
}

enum class ReleaseDecision {
    GO,
    NO_GO,
}

data class AcceptanceCheck(
    val id: String,
    val status: AcceptanceStatus,
    val requiredForGo: Boolean,
)

data class ReleaseDefect(
    val severity: DefectSeverity,
)

data class ReleaseAcceptanceSnapshot(
    val checks: List<AcceptanceCheck>,
    val defects: List<ReleaseDefect>,
)

data class ReleaseDecisionResult(
    val decision: ReleaseDecision,
    val reasons: List<String>,
)

class ReleaseAcceptanceDecider {
    fun decide(snapshot: ReleaseAcceptanceSnapshot): ReleaseDecisionResult {
        val reasons = mutableListOf<String>()

        val requiredChecks = snapshot.checks.filter { it.requiredForGo }
        val missingRequired = requiredChecks.filter { it.status != AcceptanceStatus.PASS }
        if (missingRequired.isNotEmpty()) {
            reasons += "required_checks_not_passed=${missingRequired.joinToString(",") { it.id }}"
        }

        val hasBlockingDefects =
            snapshot.defects.any { defect ->
                defect.severity == DefectSeverity.BLOCKER || defect.severity == DefectSeverity.MAJOR
            }
        if (hasBlockingDefects) {
            reasons += "blocking_or_major_defects_present"
        }

        val decision =
            if (reasons.isEmpty()) {
                ReleaseDecision.GO
            } else {
                ReleaseDecision.NO_GO
            }
        return ReleaseDecisionResult(decision = decision, reasons = reasons)
    }
}
