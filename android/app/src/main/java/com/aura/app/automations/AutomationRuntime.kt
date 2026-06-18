package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutomationRuntime(
    private val repository: AutomationRepository,
    private val geofenceRegistrar: AutomationGeofenceRegistrar,
    private val scheduleScheduler: AutomationScheduleScheduler,
    private val flowContinuationScheduler: AutomationFlowContinuationScheduler = NoOpAutomationFlowContinuationScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val restoreMutex = Mutex()

    suspend fun restoreTriggers() = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            val automations = repository.list()
            val failures = mutableListOf<Exception>()
            captureFailure { geofenceRegistrar.restore(automations) }?.let(failures::add)
            captureFailure { scheduleScheduler.restore(automations) }?.let(failures::add)
            captureFailure { restoreFlowContinuations(automations) }?.let(failures::add)
            failures.throwIfNotEmpty()
        }
    }

    suspend fun upsertAndRestore(spec: AutomationSpec): AutomationSpec = withContext(Dispatchers.IO) {
        val saved = repository.upsert(spec)
        restoreTriggers()
        saved
    }

    suspend fun deleteAndRestore(id: String) = withContext(Dispatchers.IO) {
        repository.activeRuns(id).forEach { run ->
            runCatching { flowContinuationScheduler.cancel(run.id) }
        }
        repository.delete(id)
        runCatching { geofenceRegistrar.remove(id) }
        runCatching { scheduleScheduler.cancel(id) }
        restoreTriggers()
    }

    private suspend fun restoreFlowContinuations(automations: List<AutomationSpec>) {
        val automationById = automations.associateBy { it.id }
        val failures = mutableListOf<Exception>()
        repository.activeRuns().forEach { run ->
            captureFailure { restoreFlowContinuation(run, automationById) }?.let(failures::add)
        }
        failures.throwIfNotEmpty()
    }

    private suspend fun restoreFlowContinuation(
        run: AutomationRunRecord,
        automationById: Map<String, AutomationSpec>
    ) {
        val spec = automationById[run.automationId]
        if (spec == null) {
            terminalizeRun(run, AutomationRunStatus.Failed, "Automation not found")
            return
        }
        if (!spec.enabled) {
            terminalizeRun(run, AutomationRunStatus.Skipped, "Automation is disabled")
            return
        }
        if (run.status == AutomationRunStatus.Running) {
            terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was interrupted before completion")
            return
        }
        if (run.automationRevision != repository.revision(spec)) {
            terminalizeRun(run, AutomationRunStatus.Failed, "Automation changed while run was waiting")
            return
        }
        val waitingStep = repository.stepRuns(run.id)
            .lastOrNull { it.status == AutomationRunStatus.Waiting }
        if (waitingStep == null) {
            terminalizeRun(run, AutomationRunStatus.Failed, "Automation run is waiting without a resumable step")
            return
        }
        val flowStep = spec.flow?.steps.orEmpty().firstOrNull { it.id == waitingStep.stepId }
        if (flowStep == null) {
            terminalizeRun(run, AutomationRunStatus.Failed, "Automation run waiting step is no longer configured")
            return
        }
        when (flowStep.type) {
            AutomationFlowStepTypes.Wait -> {
                try {
                    flowContinuationScheduler.schedule(run.id, flowStep.remainingWaitMillis(waitingStep))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    terminalizeRun(
                        run,
                        AutomationRunStatus.Failed,
                        "Failed to restore flow continuation: ${error.message ?: error::class.simpleName}"
                    )
                }
            }
            AutomationFlowStepTypes.Checkpoint -> flowContinuationScheduler.cancel(run.id)
            else -> terminalizeRun(
                run,
                AutomationRunStatus.Failed,
                "Automation run waiting step is no longer resumable"
            )
        }
    }

    private suspend fun terminalizeRun(run: AutomationRunRecord, status: String, message: String) {
        repository.updateRun(run.id, status, message)
        flowContinuationScheduler.cancel(run.id)
        repository.log(run.automationId, run.eventType, status, message)
    }

    private fun AutomationFlowStep.remainingWaitMillis(waitingStep: AutomationStepRunRecord): Long {
        val waitStartedAt = waitingStep.completedAt ?: waitingStep.startedAt
        val elapsedMillis = clock() - waitStartedAt
        return (waitMillis - elapsedMillis).coerceAtLeast(0L)
    }

    private fun List<Exception>.throwIfNotEmpty() {
        if (isNotEmpty()) throw AutomationRestoreException(this)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Exception? =
        try {
            block()
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error
        }
}

internal class AutomationRestoreException(failures: List<Exception>) : Exception(
    failures.joinToString(
        prefix = "Automation trigger restoration failed: ",
        separator = "; "
    ) { it.message ?: it::class.simpleName ?: "Unknown error" },
    failures.firstOrNull()
) {
    init {
        failures.drop(1).forEach(::addSuppressed)
    }
}
