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
    private val executionRegistry: AutomationExecutionRegistry = AutomationExecutionRegistry(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val stateMutexes = List(StateMutexCount) { Mutex() }

    suspend fun handle(event: AutomationEvent): List<AutomationRunResult> =
        repository.listEnabled()
            .filter { triggerMatcher.matches(it, event) }
            .map { runAutomation(it, event) }

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

    suspend fun resumeRun(runId: String, values: Map<String, String> = emptyMap()): AutomationRunResult {
        val requestedRun = repository.getRun(runId)
            ?: return AutomationRunResult("", AutomationRunStatus.Failed, "Automation run not found", runId = runId)
        if (requestedRun.status != AutomationRunStatus.Waiting) {
            return AutomationRunResult(
                requestedRun.automationId,
                AutomationRunStatus.Skipped,
                "Automation run is not waiting",
                runId = runId
            )
        }
        val executionGeneration = executionRegistry.generation(requestedRun.automationId)
        val expectedWaitingStepId = repository.stepRuns(runId)
            .lastOrNull { it.status == AutomationRunStatus.Waiting }
            ?.id
        val preparation = stateMutex("run:$runId").withLock {
            prepareResume(runId, expectedWaitingStepId, values)
        }
        return when (preparation) {
            is ResumePreparation.Ready -> runAutomation(
                spec = preparation.spec,
                event = preparation.event,
                existingRunId = runId,
                startStepIndex = preparation.startStepIndex,
                executionGeneration = executionGeneration
            )
            is ResumePreparation.Rejected -> preparation.result
        }
    }

    suspend fun failWaitingRun(runId: String, message: String): AutomationRunResult =
        stateMutex("run:$runId").withLock {
            val run = repository.getRun(runId)
                ?: return@withLock AutomationRunResult(
                    "",
                    AutomationRunStatus.Failed,
                    "Automation run not found",
                    runId = runId
                )
            if (run.status != AutomationRunStatus.Waiting) {
                return@withLock AutomationRunResult(
                    run.automationId,
                    AutomationRunStatus.Skipped,
                    "Automation run is not waiting",
                    runId = runId
                )
            }
            terminalizeRun(run, AutomationRunStatus.Failed, message)
        }

    private suspend fun prepareResume(
        runId: String,
        expectedWaitingStepId: String?,
        values: Map<String, String>
    ): ResumePreparation {
        val run = repository.getRun(runId)
            ?: return ResumePreparation.Rejected(
                AutomationRunResult("", AutomationRunStatus.Failed, "Automation run not found", runId = runId)
            )
        if (run.status != AutomationRunStatus.Waiting) {
            return ResumePreparation.Rejected(
                AutomationRunResult(
                    run.automationId,
                    AutomationRunStatus.Skipped,
                    "Automation run is not waiting",
                    runId = runId
                )
            )
        }
        val waitingStep = repository.stepRuns(runId)
            .lastOrNull { it.status == AutomationRunStatus.Waiting }
            ?: return ResumePreparation.Rejected(
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation run is waiting without a resumable step")
            )
        if (waitingStep.id != expectedWaitingStepId) {
            return ResumePreparation.Rejected(
                AutomationRunResult(
                    run.automationId,
                    AutomationRunStatus.Skipped,
                    "Automation run advanced before this resume request",
                    runId = runId
                )
            )
        }
        val spec = repository.get(run.automationId)
            ?: return ResumePreparation.Rejected(
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation not found")
            )
        if (!spec.enabled) {
            return ResumePreparation.Rejected(
                terminalizeRun(run, AutomationRunStatus.Skipped, "Automation is disabled")
            )
        }
        if (run.automationRevision != repository.revision(spec)) {
            return ResumePreparation.Rejected(
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation changed while run was waiting")
            )
        }
        repository.updateRun(
            runId = run.id,
            status = AutomationRunStatus.Running,
            message = "Automation flow resumed",
            completed = false
        )
        return ResumePreparation.Ready(
            spec = spec,
            event = AutomationEvent(
                type = run.eventType,
                automationId = run.automationId,
                values = run.values + values
            ),
            startStepIndex = waitingStep.stepIndex + 1
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
        return runAutomation(
            spec,
            event,
            existingRunId = null,
            startStepIndex = 0,
            executionGeneration = executionRegistry.generation(spec.id)
        )
    }

    private suspend fun runAutomation(
        spec: AutomationSpec,
        event: AutomationEvent,
        existingRunId: String?,
        startStepIndex: Int,
        executionGeneration: Long
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

        val existingRun = existingRunId?.let { repository.getRun(it) }
        if (existingRunId != null && existingRun == null) {
            return result(
                spec.id,
                event.type,
                AutomationRunStatus.Failed,
                "Automation run not found",
                runId = existingRunId
            )
        }
        val enrichedEvent = try {
            contextEnricher.enrich(spec, event)
        } catch (error: CancellationException) {
            existingRun?.let { run ->
                withContext(NonCancellable) {
                    terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was cancelled")
                }
            }
            throw error
        } catch (error: Exception) {
            val message = failureMessage("Automation context enrichment failed", error)
            return existingRun?.let { terminalizeRun(it, AutomationRunStatus.Failed, message) }
                ?: result(spec.id, event.type, AutomationRunStatus.Failed, message)
        }
        if (!conditionEvaluator.passes(spec.conditions, enrichedEvent)) {
            return result(
                spec.id,
                event.type,
                AutomationRunStatus.Skipped,
                "Conditions did not pass",
                existingRunId = existingRunId
            )
        }
        val run = if (existingRun != null) {
            existingRun
        } else {
            when (val preparation = prepareNewRun(spec, event, enrichedEvent)) {
                is StartPreparation.Ready -> preparation.run
                is StartPreparation.Rejected -> return preparation.result
            }
        }
        val executeSteps: suspend () -> AutomationRunResult = {
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
        }
        return try {
            executionRegistry.track(spec.id, run.id, executionGeneration) {
                if (steps.drop(startStepIndex).any { it.action?.type in AutomationActionTypeSets.CrossApp }) {
                    CrossAppExecutionMutex.withLock { executeSteps() }
                } else {
                    executeSteps()
                }
            }
        } catch (error: AutomationConfigurationChangedException) {
            withContext(NonCancellable) {
                try {
                    terminalizeRun(run, error.terminalStatus, error.message ?: "Automation configuration changed")
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was cancelled")
            }
            throw error
        } catch (error: Exception) {
            recoverRunFailure(run, error)
        }
    }

    private suspend fun recoverRunFailure(
        run: AutomationRunRecord,
        error: Exception
    ): AutomationRunResult {
        val message = failureMessage("Automation execution failed", error)
        return try {
            withContext(NonCancellable) {
                terminalizeRun(run, AutomationRunStatus.Failed, message)
            }
        } catch (cleanupError: Exception) {
            error.addSuppressed(cleanupError)
            throw error
        }
    }

    private suspend fun prepareNewRun(
        spec: AutomationSpec,
        event: AutomationEvent,
        enrichedEvent: AutomationEvent
    ): StartPreparation = stateMutex("automation:${spec.id}").withLock {
        val currentSpec = repository.get(spec.id)
            ?: return@withLock StartPreparation.Rejected(
                result(spec.id, event.type, AutomationRunStatus.Failed, "Automation not found")
            )
        if (!currentSpec.enabled) {
            return@withLock StartPreparation.Rejected(
                result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is disabled")
            )
        }
        if (repository.revision(currentSpec) != repository.revision(spec)) {
            return@withLock StartPreparation.Rejected(
                result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation changed before execution")
            )
        }
        if (spec.flow?.concurrencyPolicy != AutomationConcurrencyPolicies.AllowParallel) {
            val activeRun = repository.activeRun(spec.id)
            if (activeRun != null) {
                return@withLock StartPreparation.Rejected(
                    result(
                        spec.id,
                        event.type,
                        AutomationRunStatus.Skipped,
                        "Automation already has an active run",
                        activeRun.id
                    )
                )
            }
        }
        val lastTriggeredAt = repository.lastTriggeredAt(spec.id)
        if (lastTriggeredAt != null && isCoolingDown(lastTriggeredAt, clock(), spec.cooldownMillis)) {
            return@withLock StartPreparation.Rejected(
                result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is cooling down")
            )
        }
        val run = repository.createRun(spec.id, enrichedEvent.type, enrichedEvent.values)
        try {
            repository.markTriggered(spec.id)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was cancelled")
            }
            throw error
        } catch (error: Exception) {
            return@withLock StartPreparation.Rejected(
                terminalizeRun(
                    run,
                    AutomationRunStatus.Failed,
                    failureMessage("Failed to persist automation trigger", error)
                )
            )
        }
        StartPreparation.Ready(run)
    }

    private fun stateMutex(key: String): Mutex =
        stateMutexes[Math.floorMod(key.hashCode(), stateMutexes.size)]

    private fun isCoolingDown(lastTriggeredAt: Long, now: Long, cooldownMillis: Long): Boolean =
        cooldownMillis > 0L &&
            lastTriggeredAt >= 0L &&
            now >= lastTriggeredAt &&
            now - lastTriggeredAt < cooldownMillis

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

    private sealed class StartPreparation {
        data class Ready(val run: AutomationRunRecord) : StartPreparation()
        data class Rejected(val result: AutomationRunResult) : StartPreparation()
    }

    private sealed class ResumePreparation {
        data class Ready(
            val spec: AutomationSpec,
            val event: AutomationEvent,
            val startStepIndex: Int
        ) : ResumePreparation()

        data class Rejected(val result: AutomationRunResult) : ResumePreparation()
    }

    private companion object {
        const val StateMutexCount = 64
        val CrossAppExecutionMutex = Mutex()
    }
}
