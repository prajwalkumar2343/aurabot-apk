package com.aura.app.dreams

import com.aura.app.automations.AutomationSpec

enum class DreamRunStatus {
    Running,
    Completed,
    Failed,
    Cancelled
}

enum class DreamRunStage {
    Admitted,
    Collecting,
    Detecting,
    Validating,
    Publishing,
    Completed
}

enum class DreamSignalKind {
    AutomationFailure,
    StaleTodo,
    MiniAppEvolution,
    RepeatedRoutine
}

enum class DreamProposalType {
    AutomationRepair,
    TodoRescue,
    MiniAppEvolution,
    RoutineAutomation
}

enum class DreamProposalStatus {
    PendingReview,
    Applying,
    Applied,
    Dismissed,
    Snoozed,
    Suppressed,
    Failed,
    Stale,
    ReconciliationRequired
}

enum class DreamRisk {
    Low,
    Medium,
    High
}

data class DreamWindow(
    val startMillis: Long,
    val endMillis: Long
) {
    init {
        require(startMillis >= 0L) { "Dream window start cannot be negative" }
        require(endMillis > startMillis) { "Dream window must have a positive duration" }
    }
}

data class DreamSignal(
    val id: String,
    val runId: String,
    val kind: DreamSignalKind,
    val subjectId: String,
    val fingerprint: String,
    val summary: String,
    val attributes: Map<String, String>,
    val occurredAt: Long,
    val confidence: Float,
    val expiresAt: Long
)

data class DreamProposalPayload(
    val automationSpec: AutomationSpec? = null,
    val miniAppId: String? = null,
    val miniAppVersion: Int? = null,
    val revisionInstruction: String? = null,
    val todoId: String? = null,
    val todoTitle: String? = null
)

data class DreamProposalDraft(
    val type: DreamProposalType,
    val fingerprint: String,
    val subjectId: String,
    val title: String,
    val summary: String,
    val rationale: String,
    val confidence: Float,
    val risk: DreamRisk,
    val evidenceIds: List<String>,
    val baseRevision: String? = null,
    val payload: DreamProposalPayload,
    val applicable: Boolean,
    val validationMessage: String
)

data class DreamProposal(
    val id: String,
    val runId: String,
    val type: DreamProposalType,
    val status: DreamProposalStatus,
    val fingerprint: String,
    val subjectId: String,
    val title: String,
    val summary: String,
    val rationale: String,
    val confidence: Float,
    val risk: DreamRisk,
    val evidenceIds: List<String>,
    val baseRevision: String?,
    val payload: DreamProposalPayload,
    val applicable: Boolean,
    val validationMessage: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class DreamRun(
    val id: String,
    val status: DreamRunStatus,
    val stage: DreamRunStage,
    val window: DreamWindow,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val signalCount: Int,
    val proposalCount: Int,
    val warningCount: Int,
    val errorMessage: String?
)

data class DreamEvidenceBatch(
    val signals: List<DreamSignal>,
    val warnings: List<String> = emptyList()
)

data class DreamReport(
    val run: DreamRun,
    val proposals: List<DreamProposal>
)

sealed interface DreamApplyResult {
    data class Applied(val proposal: DreamProposal) : DreamApplyResult
    data class Rejected(val reason: String) : DreamApplyResult
    data class Failed(val reason: String) : DreamApplyResult
}
