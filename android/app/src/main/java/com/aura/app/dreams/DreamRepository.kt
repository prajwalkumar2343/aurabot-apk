package com.aura.app.dreams

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class DreamRunAdmission(val run: DreamRun, val admitted: Boolean)

class DreamRepository(
    private val dao: DreamDao,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type

    val latestRun: Flow<DreamRun?> = dao.observeLatestRun().map { it?.run() }
    val reviewableProposals: Flow<List<DreamProposal>> = dao.observeReviewableProposals().map { rows ->
        rows.mapNotNull { it.proposalOrNull() }
    }

    suspend fun admit(window: DreamWindow): DreamRunAdmission {
        dao.activeRun()?.let { return DreamRunAdmission(it.run(), false) }
        dao.runForWindow(window.startMillis, window.endMillis)?.let {
            return DreamRunAdmission(it.run(), false)
        }
        val now = clock()
        val entity = DreamRunEntity(
            id = UUID.randomUUID().toString(),
            status = DreamRunStatus.Running.name,
            stage = DreamRunStage.Admitted.name,
            windowStart = window.startMillis,
            windowEnd = window.endMillis,
            startedAt = now,
            updatedAt = now,
            completedAt = null,
            signalCount = 0,
            proposalCount = 0,
            warningCount = 0,
            errorMessage = null
        )
        val inserted = dao.insertRun(entity) != -1L
        val stored = dao.runForWindow(window.startMillis, window.endMillis) ?: entity
        return DreamRunAdmission(stored.run(), inserted && stored.id == entity.id)
    }

    suspend fun advance(
        runId: String,
        stage: DreamRunStage,
        signalCount: Int,
        proposalCount: Int,
        warningCount: Int
    ) {
        check(
            dao.advanceRun(
                id = runId,
                stage = stage.name,
                updatedAt = clock(),
                signalCount = signalCount,
                proposalCount = proposalCount,
                warningCount = warningCount
            ) == 1
        ) { "Dream run '$runId' is no longer active" }
    }

    suspend fun finish(
        runId: String,
        status: DreamRunStatus,
        signalCount: Int,
        proposalCount: Int,
        warningCount: Int,
        errorMessage: String? = null
    ): DreamRun {
        val now = clock()
        dao.finishRun(
            id = runId,
            status = status.name,
            stage = DreamRunStage.Completed.name,
            updatedAt = now,
            completedAt = now,
            signalCount = signalCount,
            proposalCount = proposalCount,
            warningCount = warningCount,
            errorMessage = errorMessage?.let(DreamPrivacyPolicy::sanitizeDiagnostic)
        )
        return requireNotNull(dao.run(runId)) { "Dream run '$runId' disappeared" }.run()
    }

    suspend fun insertSignals(signals: List<DreamSignal>): List<DreamSignal> {
        if (signals.isEmpty()) return emptyList()
        dao.insertSignals(signals.map { it.entity() })
        return dao.signalsForRun(signals.first().runId, MaxSignalsPerRun).mapNotNull { it.signalOrNull() }
    }

    suspend fun signals(runId: String): List<DreamSignal> =
        dao.signalsForRun(runId, MaxSignalsPerRun).mapNotNull { it.signalOrNull() }

    suspend fun insertProposals(runId: String, drafts: List<DreamProposalDraft>): List<DreamProposal> {
        val now = clock()
        val entities = drafts.mapNotNull { draft ->
            val suppression = dao.suppression(draft.fingerprint)
            val suppressed = suppression != null && (suppression.expiresAt == null || suppression.expiresAt > now)
            if (suppressed || dao.activeProposalForFingerprint(draft.fingerprint) != null) {
                null
            } else {
                DreamProposalEntity(
                    id = UUID.randomUUID().toString(),
                    runId = runId,
                    type = draft.type.name,
                    status = DreamProposalStatus.PendingReview.name,
                    fingerprint = draft.fingerprint,
                    subjectId = draft.subjectId,
                    title = draft.title.take(MaxTitleLength),
                    summary = draft.summary.take(MaxSummaryLength),
                    rationale = draft.rationale.take(MaxRationaleLength),
                    confidence = draft.confidence.coerceIn(0f, 1f),
                    risk = draft.risk.name,
                    evidenceIdsJson = gson.toJson(draft.evidenceIds.distinct().take(MaxEvidenceIds)),
                    baseRevision = draft.baseRevision,
                    payloadJson = gson.toJson(draft.payload),
                    applicable = draft.applicable,
                    validationMessage = draft.validationMessage.take(MaxValidationLength),
                    createdAt = now,
                    updatedAt = now,
                    decisionAt = null
                )
            }
        }
        if (entities.isNotEmpty()) dao.insertProposals(entities)
        return dao.proposalsForRun(runId).mapNotNull { it.proposalOrNull() }
    }

    suspend fun report(runId: String): DreamReport? {
        val run = dao.run(runId)?.run() ?: return null
        return DreamReport(run, dao.proposalsForRun(runId).mapNotNull { it.proposalOrNull() })
    }

    suspend fun proposal(id: String): DreamProposal? = dao.proposal(id)?.proposalOrNull()

    suspend fun evidence(proposalId: String): List<DreamSignal> {
        val proposal = proposal(proposalId) ?: return emptyList()
        if (proposal.evidenceIds.isEmpty()) return emptyList()
        return dao.signals(proposal.evidenceIds).mapNotNull { it.signalOrNull() }
            .sortedByDescending { it.occurredAt }
    }

    suspend fun claimForApplication(id: String): DreamProposal? {
        if (dao.claimProposal(id, clock()) != 1) return null
        return proposal(id)
    }

    suspend fun markApplied(id: String, message: String): DreamProposal? {
        transition(
            id = id,
            status = DreamProposalStatus.Applied,
            message = message,
            allowed = listOf(DreamProposalStatus.Applying)
        )
        return proposal(id)
    }

    suspend fun markApplicationFailed(id: String, message: String): DreamProposal? {
        transition(
            id = id,
            status = DreamProposalStatus.Failed,
            message = DreamPrivacyPolicy.sanitizeDiagnostic(message),
            allowed = listOf(DreamProposalStatus.Applying)
        )
        return proposal(id)
    }

    suspend fun markReconciliationRequired(id: String, message: String): DreamProposal? {
        transition(
            id = id,
            status = DreamProposalStatus.ReconciliationRequired,
            message = DreamPrivacyPolicy.sanitizeDiagnostic(message),
            allowed = listOf(DreamProposalStatus.Applying)
        )
        return proposal(id)
    }

    suspend fun dismiss(id: String): Boolean = transition(
        id = id,
        status = DreamProposalStatus.Dismissed,
        message = "Dismissed by user",
        allowed = listOf(
            DreamProposalStatus.PendingReview,
            DreamProposalStatus.Snoozed,
            DreamProposalStatus.Failed,
            DreamProposalStatus.Stale,
            DreamProposalStatus.ReconciliationRequired
        )
    )

    suspend fun suppress(id: String): Boolean {
        val proposal = proposal(id) ?: return false
        val now = clock()
        dao.upsertSuppression(
            DreamSuppressionEntity(
                fingerprint = proposal.fingerprint,
                reason = "Suppressed by user",
                createdAt = now,
                expiresAt = null
            )
        )
        return transition(
            id = id,
            status = DreamProposalStatus.Suppressed,
            message = "This pattern will not be suggested again",
            allowed = listOf(
                DreamProposalStatus.PendingReview,
                DreamProposalStatus.Snoozed,
                DreamProposalStatus.Failed,
                DreamProposalStatus.Stale,
                DreamProposalStatus.ReconciliationRequired
            )
        )
    }

    suspend fun recoverInterruptedApplications(): Int = dao.recoverInterruptedApplications(clock())

    suspend fun trace(runId: String, stage: DreamRunStage, eventType: String, status: String, details: String) {
        dao.insertTrace(
            DreamTraceEntity(
                id = UUID.randomUUID().toString(),
                runId = runId,
                stage = stage.name,
                eventType = eventType.take(80),
                status = status.take(40),
                details = DreamPrivacyPolicy.sanitizeDiagnostic(details),
                createdAt = clock()
            )
        )
    }

    suspend fun prune() {
        val now = clock()
        dao.pruneSignals(now)
        dao.pruneSuppressions(now)
    }

    suspend fun deleteAll() = dao.deleteAllDreamData()

    private suspend fun transition(
        id: String,
        status: DreamProposalStatus,
        message: String,
        allowed: List<DreamProposalStatus>
    ): Boolean {
        val now = clock()
        return dao.transitionProposal(
            id = id,
            status = status.name,
            updatedAt = now,
            decisionAt = now,
            message = message.take(MaxValidationLength),
            allowedStatuses = allowed.map { it.name }
        ) == 1
    }

    private fun DreamRunEntity.run() = DreamRun(
        id = id,
        status = enumValueOrDefault(status, DreamRunStatus.Failed),
        stage = enumValueOrDefault(stage, DreamRunStage.Completed),
        window = DreamWindow(windowStart, windowEnd),
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        signalCount = signalCount,
        proposalCount = proposalCount,
        warningCount = warningCount,
        errorMessage = errorMessage
    )

    private fun DreamSignal.entity() = DreamSignalEntity(
        id = id,
        runId = runId,
        kind = kind.name,
        subjectId = subjectId,
        fingerprint = fingerprint,
        summary = summary.take(MaxSummaryLength),
        attributesJson = gson.toJson(attributes),
        occurredAt = occurredAt,
        confidence = confidence.coerceIn(0f, 1f),
        expiresAt = expiresAt
    )

    private fun DreamSignalEntity.signalOrNull(): DreamSignal? = runCatching {
        DreamSignal(
            id = id,
            runId = runId,
            kind = enumValueOf(kind),
            subjectId = subjectId,
            fingerprint = fingerprint,
            summary = summary,
            attributes = gson.fromJson<Map<String, String>>(attributesJson, stringMapType).orEmpty(),
            occurredAt = occurredAt,
            confidence = confidence,
            expiresAt = expiresAt
        )
    }.getOrNull()

    private fun DreamProposalEntity.proposalOrNull(): DreamProposal? = runCatching {
        DreamProposal(
            id = id,
            runId = runId,
            type = enumValueOf(type),
            status = enumValueOf(status),
            fingerprint = fingerprint,
            subjectId = subjectId,
            title = title,
            summary = summary,
            rationale = rationale,
            confidence = confidence,
            risk = enumValueOf(risk),
            evidenceIds = gson.fromJson<List<String>>(evidenceIdsJson, stringListType).orEmpty(),
            baseRevision = baseRevision,
            payload = gson.fromJson(payloadJson, DreamProposalPayload::class.java),
            applicable = applicable,
            validationMessage = validationMessage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private companion object {
        const val MaxSignalsPerRun = 500
        const val MaxEvidenceIds = 20
        const val MaxTitleLength = 120
        const val MaxSummaryLength = 500
        const val MaxRationaleLength = 1_000
        const val MaxValidationLength = 500
    }
}
