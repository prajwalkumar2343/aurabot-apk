package com.aura.app.automations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutomationRuntime(
    private val repository: AutomationRepository,
    private val geofenceRegistrar: AutomationGeofenceRegistrar,
    private val scheduleScheduler: AutomationScheduleScheduler,
    private val flowContinuationScheduler: AutomationFlowContinuationScheduler = NoOpAutomationFlowContinuationScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun restoreTriggers() = withContext(Dispatchers.IO) {
        val automations = repository.list()
        geofenceRegistrar.restore(automations)
        scheduleScheduler.restore(automations)
        restoreFlowContinuations(automations)
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
        repository.activeRuns().forEach { run ->
            val spec = automationById[run.automationId]
            if (spec == null) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation not found")
                return@forEach
            }
            if (!spec.enabled) {
                terminalizeRun(run, AutomationRunStatus.Skipped, "Automation is disabled")
                return@forEach
            }
            if (run.status == AutomationRunStatus.Running) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation run was interrupted before completion")
                return@forEach
            }
            if (run.automationRevision != repository.revision(spec)) {
                terminalizeRun(run, AutomationRunStatus.Failed, "Automation changed while run was waiting")
                return@forEach
            }
            val waitingStep = repository.stepRuns(run.id)
                .lastOrNull { it.status == AutomationRunStatus.Waiting }
                ?: return@forEach terminalizeRun(
                    run,
                    AutomationRunStatus.Failed,
                    "Automation run is waiting without a resumable step"
                )
            val flowStep = spec.flow?.steps.orEmpty().firstOrNull { it.id == waitingStep.stepId }
                ?: return@forEach terminalizeRun(
                    run,
                    AutomationRunStatus.Failed,
                    "Automation run waiting step is no longer configured"
                )
            when (flowStep.type) {
                AutomationFlowStepTypes.Wait -> flowContinuationScheduler.schedule(
                    run.id,
                    flowStep.remainingWaitMillis(waitingStep)
                )
                AutomationFlowStepTypes.Checkpoint -> flowContinuationScheduler.cancel(run.id)
                else -> terminalizeRun(
                    run,
                    AutomationRunStatus.Failed,
                    "Automation run waiting step is no longer resumable"
                )
            }
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
}
