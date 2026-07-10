package com.aura.app.dreams

import com.aura.app.assistant.AssistantRepository
import com.aura.app.automations.AutomationActionMetadata
import com.aura.app.automations.AutomationRepository
import com.aura.app.automations.AutomationTrigger
import com.aura.app.automations.AutomationTriggerTypes
import com.aura.app.automations.AutomationValidator
import com.aura.app.automations.ScheduleTrigger
import com.aura.app.miniapps.MiniAppRepository

class DreamProposalEngine(
    private val automationRepository: AutomationRepository,
    private val assistantRepository: AssistantRepository,
    private val miniAppRepository: MiniAppRepository
) {
    suspend fun build(signals: List<DreamSignal>, maxProposals: Int): List<DreamProposalDraft> {
        val todos = runCatching { assistantRepository.todos().associateBy { it.id } }.getOrDefault(emptyMap())
        return signals.sortedWith(
            compareByDescending<DreamSignal> { DreamScorer.score(it) }.thenBy { it.fingerprint }
        ).mapNotNull { signal ->
            when (signal.kind) {
                DreamSignalKind.AutomationFailure -> automationRepair(signal)
                DreamSignalKind.StaleTodo -> {
                    val todo = todos[signal.subjectId] ?: return@mapNotNull null
                    if (todo.done) null else todoRescue(signal, todo.title)
                }
                DreamSignalKind.MiniAppEvolution -> miniAppEvolution(signal)
                DreamSignalKind.RepeatedRoutine -> routineAutomation(signal)
            }
        }.distinctBy { it.fingerprint }
            .take(maxProposals.coerceIn(1, 10))
    }

    private suspend fun automationRepair(signal: DreamSignal): DreamProposalDraft? {
        val current = automationRepository.get(signal.subjectId) ?: return null
        val baseRevision = signal.attributes["revision"].orEmpty()
        if (baseRevision.isBlank() || DreamAutomationRevision.compute(current) != baseRevision) return null
        val stepId = signal.attributes["stepId"].orEmpty()
        val failureKind = signal.attributes["failureKind"].orEmpty()
        val oldTimeout = signal.attributes["timeoutMillis"]?.toLongOrNull() ?: DefaultTimeoutMillis
        val repaired = if (failureKind == "timeout" && stepId.isNotBlank()) {
            current.flow?.let { flow ->
                val changed = flow.steps.any { it.id == stepId && it.action != null }
                if (!changed) null else current.copy(
                    flow = flow.copy(
                        steps = flow.steps.map { step ->
                            if (step.id == stepId && step.action != null) {
                                step.copy(
                                    action = step.action.copy(
                                        metadata = step.action.metadata + (
                                            AutomationActionMetadata.TimeoutMillis to
                                                (oldTimeout * 2).coerceIn(MinTimeoutMillis, MaxTimeoutMillis).toString()
                                            )
                                    )
                                )
                            } else {
                                step
                            }
                        }
                    )
                )
            }
        } else {
            null
        }
        val validRepair = repaired?.let { runCatching { AutomationValidator.validate(it) }.getOrNull() }
        val applicable = validRepair != null
        val validation = if (applicable) {
            "Validated a conservative timeout repair. No live cross-app actions were executed."
        } else {
            "Aura diagnosed the repeated failure, but no safe mechanical repair is available."
        }
        return DreamProposalDraft(
            type = DreamProposalType.AutomationRepair,
            fingerprint = signal.fingerprint,
            subjectId = signal.subjectId,
            title = if (applicable) "Repair ${signal.attributes["automationName"] ?: "automation"}" else "Review repeated automation failure",
            summary = signal.summary,
            rationale = "The same revision and step failed ${signal.attributes["count"] ?: "multiple"} times without a later successful run.",
            confidence = signal.confidence,
            risk = DreamRisk.Medium,
            evidenceIds = listOf(signal.id),
            baseRevision = baseRevision,
            payload = DreamProposalPayload(automationSpec = validRepair),
            applicable = applicable,
            validationMessage = validation
        )
    }

    private fun todoRescue(signal: DreamSignal, title: String) = DreamProposalDraft(
        type = DreamProposalType.TodoRescue,
        fingerprint = signal.fingerprint,
        subjectId = signal.subjectId,
        title = "Rescue an old task",
        summary = "“${title.take(120)}” has remained open for ${signal.attributes["ageDays"] ?: "several"} days.",
        rationale = "Aura can help make the next action smaller, but will not rewrite or delete the task automatically.",
        confidence = signal.confidence,
        risk = DreamRisk.Low,
        evidenceIds = listOf(signal.id),
        payload = DreamProposalPayload(todoId = signal.subjectId, todoTitle = title.take(200)),
        applicable = false,
        validationMessage = "Review-only proposal; the existing task remains unchanged."
    )

    private suspend fun miniAppEvolution(signal: DreamSignal): DreamProposalDraft? {
        val bundle = miniAppRepository.bundle(signal.subjectId) ?: return null
        val expectedVersion = signal.attributes["miniAppVersion"]?.toIntOrNull() ?: return null
        if (bundle.version != expectedVersion) return null
        val instruction = signal.attributes["revisionInstruction"]?.takeIf { it.isNotBlank() } ?: return null
        return DreamProposalDraft(
            type = DreamProposalType.MiniAppEvolution,
            fingerprint = signal.fingerprint,
            subjectId = signal.subjectId,
            title = signal.attributes["suggestionTitle"] ?: "Evolve ${signal.attributes["miniAppName"] ?: "mini app"}",
            summary = signal.summary,
            rationale = "The suggestion comes from repeated patterns in this mini app's local records.",
            confidence = signal.confidence,
            risk = DreamRisk.Medium,
            evidenceIds = listOf(signal.id),
            baseRevision = expectedVersion.toString(),
            payload = DreamProposalPayload(
                miniAppId = signal.subjectId,
                miniAppVersion = expectedVersion,
                revisionInstruction = instruction
            ),
            applicable = true,
            validationMessage = "The current mini-app version matches. A final preview and migration plan will be generated after approval."
        )
    }

    private suspend fun routineAutomation(signal: DreamSignal): DreamProposalDraft? {
        val source = automationRepository.get(signal.subjectId) ?: return null
        val baseRevision = signal.attributes["revision"].orEmpty()
        if (DreamAutomationRevision.compute(source) != baseRevision) return null
        val localTime = signal.attributes["localTime"] ?: return null
        val draft = source.copy(
            id = "",
            name = "${source.name} routine",
            description = "Dream proposal based on ${signal.attributes["occurrenceCount"] ?: "repeated"} manual runs.",
            enabled = false,
            trigger = AutomationTrigger(
                type = AutomationTriggerTypes.Schedule,
                schedule = ScheduleTrigger(mode = "daily", localTime = localTime)
            ),
            createdAt = 0L,
            updatedAt = 0L
        )
        val valid = runCatching { AutomationValidator.validate(draft) }.getOrNull() ?: return null
        return DreamProposalDraft(
            type = DreamProposalType.RoutineAutomation,
            fingerprint = signal.fingerprint,
            subjectId = signal.subjectId,
            title = "Turn ${source.name} into a routine",
            summary = "Create a disabled daily draft for $localTime, ready for you to inspect and enable.",
            rationale = signal.summary,
            confidence = signal.confidence,
            risk = DreamRisk.Low,
            evidenceIds = listOf(signal.id),
            baseRevision = baseRevision,
            payload = DreamProposalPayload(automationSpec = valid),
            applicable = true,
            validationMessage = "Validated as a disabled automation draft. It will not run until you enable it."
        )
    }

    private companion object {
        const val DefaultTimeoutMillis = 5_000L
        const val MinTimeoutMillis = 1_000L
        const val MaxTimeoutMillis = 30_000L
    }
}
