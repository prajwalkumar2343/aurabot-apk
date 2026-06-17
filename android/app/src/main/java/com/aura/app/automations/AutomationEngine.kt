package com.aura.app.automations

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutomationEngine(
    private val repository: AutomationRepository,
    private val triggerMatcher: AutomationTriggerMatcher = AutomationTriggerMatcher(),
    private val conditionEvaluator: AutomationConditionEvaluator = AutomationConditionEvaluator(),
    private val contextEnricher: AutomationContextEnricher = DefaultAutomationContextEnricher(),
    private val actionExecutor: AutomationActionExecutor,
    private val flowContinuationScheduler: AutomationFlowContinuationScheduler = NoOpAutomationFlowContinuationScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()

    suspend fun handle(event: AutomationEvent): List<AutomationRunResult> = mutex.withLock {
        repository.listEnabled()
            .filter { triggerMatcher.matches(it, event) }
            .map { runAutomation(it, event) }
    }

    suspend fun runNow(automationId: String, values: Map<String, String> = emptyMap()): AutomationRunResult {
        val spec = repository.get(automationId)
            ?: return AutomationRunResult(automationId, AutomationRunStatus.Failed, "Automation not found")
        return runAutomation(
            spec = spec,
            event = AutomationEvent(
                type = AutomationEvents.Manual,
                automationId = automationId,
                values = values
            )
        )
    }

    suspend fun resumeRun(runId: String, values: Map<String, String> = emptyMap()): AutomationRunResult = mutex.withLock {
        val run = repository.getRun(runId)
            ?: return AutomationRunResult("", AutomationRunStatus.Failed, "Automation run not found", runId = runId)
        if (run.status != AutomationRunStatus.Waiting) {
            return AutomationRunResult(
                run.automationId,
                AutomationRunStatus.Skipped,
                "Automation run is not waiting",
                runId = runId
            )
        }
        val spec = repository.get(run.automationId)
            ?: return AutomationRunResult(run.automationId, AutomationRunStatus.Failed, "Automation not found", runId = runId)
        val waitingStep = repository.stepRuns(runId)
            .lastOrNull { it.status == AutomationRunStatus.Waiting }
            ?: return failWaitingRun(run, "Automation run is waiting without a resumable step")
        val nextStepIndex = waitingStep.stepIndex + 1
        return runAutomation(
            spec = spec,
            event = AutomationEvent(
                type = run.eventType,
                automationId = run.automationId,
                values = run.values + values
            ),
            existingRunId = runId,
            startStepIndex = nextStepIndex
        )
    }

    private suspend fun failWaitingRun(run: AutomationRunRecord, message: String): AutomationRunResult {
        repository.updateRun(run.id, AutomationRunStatus.Failed, message)
        flowContinuationScheduler.cancel(run.id)
        repository.log(run.automationId, run.eventType, AutomationRunStatus.Failed, message)
        return AutomationRunResult(run.automationId, AutomationRunStatus.Failed, message, runId = run.id)
    }

    private suspend fun runAutomation(spec: AutomationSpec, event: AutomationEvent): AutomationRunResult {
        return runAutomation(spec, event, existingRunId = null, startStepIndex = 0)
    }

    private suspend fun runAutomation(
        spec: AutomationSpec,
        event: AutomationEvent,
        existingRunId: String?,
        startStepIndex: Int
    ): AutomationRunResult {
        if (!spec.enabled) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is disabled")
        }
        if (existingRunId == null && spec.flow?.concurrencyPolicy != AutomationConcurrencyPolicies.AllowParallel) {
            val activeRun = repository.activeRun(spec.id)
            if (activeRun != null) {
                return result(
                    spec.id,
                    event.type,
                    AutomationRunStatus.Skipped,
                    "Automation already has an active run",
                    activeRun.id
                )
            }
        }
        val lastTriggeredAt = repository.lastTriggeredAt(spec.id)
        if (
            existingRunId == null &&
            lastTriggeredAt != null &&
            spec.cooldownMillis > 0L &&
            clock() - lastTriggeredAt < spec.cooldownMillis
        ) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is cooling down")
        }
        if (!conditionEvaluator.passes(spec.conditions, event)) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Conditions did not pass")
        }
        val steps = spec.effectiveSteps()
        if (steps.isEmpty()) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "No flow steps configured")
        }

        val enrichedEvent = contextEnricher.enrich(spec, event)
        val run = existingRunId?.let { repository.getRun(it) }
            ?: repository.createRun(spec.id, enrichedEvent.type, enrichedEvent.values)
        if (existingRunId == null) {
            repository.markTriggered(spec.id, event.occurredAt)
        }
        val stepResults = mutableListOf<AutomationStepResult>()
        val actionResults = mutableListOf<AutomationActionResult>()
        var finalStatus = AutomationRunStatus.Success
        var finalMessage = "Automation ran ${steps.size} step(s)"
        for ((index, step) in steps.withIndex().drop(startStepIndex)) {
            val stepResult = executeStep(run.id, spec.id, index, step, enrichedEvent)
            stepResults += stepResult
            stepResult.actionResult?.let { actionResults += it }
            when (stepResult.status) {
                AutomationRunStatus.Success -> Unit
                AutomationRunStatus.Waiting -> {
                    finalStatus = AutomationRunStatus.Waiting
                    finalMessage = stepResult.message
                    if (step.type == AutomationFlowStepTypes.Wait) {
                        flowContinuationScheduler.schedule(run.id, step.waitMillis)
                    }
                    break
                }
                AutomationRunStatus.Skipped -> {
                    if (!step.continueOnFailure) {
                        finalStatus = AutomationRunStatus.Skipped
                        finalMessage = stepResult.message
                        break
                    }
                }
                AutomationRunStatus.Failed -> {
                    if (!step.continueOnFailure) {
                        finalStatus = AutomationRunStatus.Failed
                        finalMessage = stepResult.message
                        break
                    }
                }
            }
        }
        repository.updateRun(run.id, finalStatus, finalMessage, enrichedEvent.values)
        if (finalStatus != AutomationRunStatus.Waiting) {
            flowContinuationScheduler.cancel(run.id)
        }
        repository.log(spec.id, event.type, finalStatus, finalMessage)
        return AutomationRunResult(spec.id, finalStatus, finalMessage, actionResults, run.id, stepResults)
    }

    private suspend fun executeStep(
        runId: String,
        automationId: String,
        stepIndex: Int,
        step: AutomationFlowStep,
        event: AutomationEvent
    ): AutomationStepResult {
        var lastActionResult: AutomationActionResult? = null
        var lastStatus = AutomationRunStatus.Failed
        var lastMessage = "Flow step failed"
        var attemptsUsed = 0
        val maxAttempts = step.retryPolicy.maxAttempts.coerceAtLeast(1)
        for (attempt in 1..maxAttempts) {
            attemptsUsed = attempt
            val execution = executeStepOnce(step, event)
            lastActionResult = execution.actionResult
            lastStatus = execution.status
            lastMessage = execution.message
            repository.recordStep(
                runId = runId,
                automationId = automationId,
                step = step,
                stepIndex = stepIndex,
                status = lastStatus,
                attempt = attempt,
                message = lastMessage
            )
            if (lastStatus != AutomationRunStatus.Failed || attempt == maxAttempts) break
            val backoffMillis = step.retryPolicy.backoffMillis.coerceAtLeast(0L)
            if (backoffMillis > 0L) delay(backoffMillis)
        }
        return AutomationStepResult(
            stepId = step.id,
            stepType = step.type,
            status = lastStatus,
            message = lastMessage,
            attempts = attemptsUsed,
            actionResult = lastActionResult
        )
    }

    private suspend fun executeStepOnce(
        step: AutomationFlowStep,
        event: AutomationEvent
    ): AutomationStepResult =
        when (step.type) {
            AutomationFlowStepTypes.Action -> {
                val action = step.action
                if (action == null) {
                    AutomationStepResult(step.id, step.type, AutomationRunStatus.Failed, "Action step is missing an action")
                } else {
                    val actionResult = actionExecutor.execute(action, event)
                    AutomationStepResult(
                        step.id,
                        step.type,
                        actionResult.status,
                        actionResult.message,
                        actionResult = actionResult
                    )
                }
            }
            AutomationFlowStepTypes.Condition -> {
                val condition = step.condition
                if (condition != null && conditionEvaluator.passes(listOf(condition), event)) {
                    AutomationStepResult(step.id, step.type, AutomationRunStatus.Success, "Condition passed")
                } else {
                    AutomationStepResult(step.id, step.type, AutomationRunStatus.Skipped, "Flow condition did not pass")
                }
            }
            AutomationFlowStepTypes.Wait -> AutomationStepResult(
                step.id,
                step.type,
                AutomationRunStatus.Waiting,
                "Flow paused for ${step.waitMillis}ms"
            )
            AutomationFlowStepTypes.Checkpoint -> AutomationStepResult(
                step.id,
                step.type,
                AutomationRunStatus.Waiting,
                step.metadata["message"] ?: "Flow paused at checkpoint"
            )
            else -> AutomationStepResult(step.id, step.type, AutomationRunStatus.Failed, "Unsupported flow step")
        }

    private suspend fun result(
        automationId: String,
        eventType: String,
        status: String,
        message: String,
        runId: String? = null
    ): AutomationRunResult {
        repository.log(automationId, eventType, status, message)
        return AutomationRunResult(automationId, status, message, runId = runId)
    }

    private fun AutomationSpec.effectiveSteps(): List<AutomationFlowStep> =
        flow?.steps?.takeIf { it.isNotEmpty() }
            ?: actions.mapIndexed { index, action ->
                AutomationFlowStep(
                    id = "action-${index + 1}",
                    name = action.title.orEmpty(),
                    type = AutomationFlowStepTypes.Action,
                    action = action
                )
            }
}
