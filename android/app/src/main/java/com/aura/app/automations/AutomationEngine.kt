package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    suspend fun runNow(automationId: String, values: Map<String, String> = emptyMap()): AutomationRunResult = mutex.withLock {
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
            ?: return terminalizeRun(run, AutomationRunStatus.Failed, "Automation not found")
        val waitingStep = repository.stepRuns(runId)
            .lastOrNull { it.status == AutomationRunStatus.Waiting }
            ?: return terminalizeRun(run, AutomationRunStatus.Failed, "Automation run is waiting without a resumable step")
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

    private suspend fun terminalizeRun(
        run: AutomationRunRecord,
        status: String,
        message: String
    ): AutomationRunResult {
        repository.updateRun(run.id, status, message)
        cancelContinuation(run.id)
        repository.log(run.automationId, run.eventType, status, message)
        return AutomationRunResult(run.automationId, status, message, runId = run.id)
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
            return result(
                spec.id,
                event.type,
                AutomationRunStatus.Skipped,
                "Automation is disabled",
                existingRunId = existingRunId
            )
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
            return result(
                spec.id,
                event.type,
                AutomationRunStatus.Skipped,
                "Conditions did not pass",
                existingRunId = existingRunId
            )
        }
        val steps = spec.effectiveSteps()
        if (steps.isEmpty()) {
            return result(
                spec.id,
                event.type,
                AutomationRunStatus.Skipped,
                "No flow steps configured",
                existingRunId = existingRunId
            )
        }

        val enrichedEvent = contextEnricher.enrich(spec, event)
        val run = existingRunId?.let { repository.getRun(it) }
            ?: repository.createRun(spec.id, enrichedEvent.type, enrichedEvent.values)
        return try {
            if (existingRunId == null) {
                repository.markTriggered(spec.id, event.occurredAt)
            }
            val stepResults = mutableListOf<AutomationStepResult>()
            val actionResults = mutableListOf<AutomationActionResult>()
            var finalStatus = AutomationRunStatus.Success
            var finalMessage = ""
            var nonBlockingSkippedSteps = 0
            var nonBlockingFailedSteps = 0
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
                            try {
                                flowContinuationScheduler.schedule(run.id, step.waitMillis)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                finalStatus = AutomationRunStatus.Failed
                                finalMessage = failureMessage("Failed to schedule flow continuation", error)
                            }
                        }
                        break
                    }
                    AutomationRunStatus.Skipped -> {
                        if (!step.continueOnFailure) {
                            finalStatus = AutomationRunStatus.Skipped
                            finalMessage = stepResult.message
                            break
                        }
                        nonBlockingSkippedSteps += 1
                    }
                    AutomationRunStatus.Failed -> {
                        if (!step.continueOnFailure) {
                            finalStatus = AutomationRunStatus.Failed
                            finalMessage = stepResult.message
                            break
                        }
                        nonBlockingFailedSteps += 1
                    }
                }
            }
            if (finalStatus == AutomationRunStatus.Success) {
                finalMessage = successMessage(
                    executedSteps = stepResults.size,
                    skippedSteps = nonBlockingSkippedSteps,
                    failedSteps = nonBlockingFailedSteps
                )
            }
            repository.updateRun(run.id, finalStatus, finalMessage, enrichedEvent.values)
            if (finalStatus != AutomationRunStatus.Waiting) {
                cancelContinuation(run.id)
            }
            repository.log(spec.id, event.type, finalStatus, finalMessage)
            AutomationRunResult(spec.id, finalStatus, finalMessage, actionResults, run.id, stepResults)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was cancelled")
            }
            throw error
        }
    }

    private fun successMessage(executedSteps: Int, skippedSteps: Int, failedSteps: Int): String {
        val base = "Automation ran $executedSteps step(s)"
        val nonBlocking = buildList {
            if (skippedSteps > 0) add("$skippedSteps non-blocking skipped")
            if (failedSteps > 0) add("$failedSteps non-blocking failed")
        }
        return if (nonBlocking.isEmpty()) base else "$base with ${nonBlocking.joinToString(" and ")} step(s)"
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
                    val actionResult = try {
                        actionExecutor.execute(action, event)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AutomationActionResult(
                            actionType = action.type,
                            status = AutomationRunStatus.Failed,
                            message = failureMessage("Action execution failed", error)
                        )
                    }
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

    private fun failureMessage(prefix: String, error: Exception): String {
        val detail = error.message?.takeIf { it.isNotBlank() }
            ?: error::class.simpleName
            ?: "Unknown error"
        return "$prefix: $detail"
    }

    private fun cancelContinuation(runId: String) {
        runCatching { flowContinuationScheduler.cancel(runId) }
    }

    private suspend fun result(
        automationId: String,
        eventType: String,
        status: String,
        message: String,
        runId: String? = null,
        existingRunId: String? = null
    ): AutomationRunResult {
        existingRunId?.let { id ->
            repository.updateRun(id, status, message)
            cancelContinuation(id)
        }
        repository.log(automationId, eventType, status, message)
        return AutomationRunResult(automationId, status, message, runId = runId ?: existingRunId)
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
