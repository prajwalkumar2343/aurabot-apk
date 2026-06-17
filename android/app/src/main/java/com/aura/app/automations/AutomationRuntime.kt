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
        repository.activeRun(id)?.let { flowContinuationScheduler.cancel(it.id) }
        repository.delete(id)
        runCatching { geofenceRegistrar.remove(id) }
        scheduleScheduler.cancel(id)
        restoreTriggers()
    }

    private suspend fun restoreFlowContinuations(automations: List<AutomationSpec>) {
        val automationById = automations.associateBy { it.id }
        repository.activeRuns().forEach { run ->
            val spec = automationById[run.automationId]
            if (spec?.enabled != true) {
                flowContinuationScheduler.cancel(run.id)
                return@forEach
            }
            val waitingStep = repository.stepRuns(run.id)
                .lastOrNull { it.status == AutomationRunStatus.Waiting }
            val flowStep = spec.flow?.steps.orEmpty().firstOrNull { it.id == waitingStep?.stepId }
            if (waitingStep != null && flowStep?.type == AutomationFlowStepTypes.Wait) {
                flowContinuationScheduler.schedule(run.id, flowStep.remainingWaitMillis(waitingStep))
            } else {
                flowContinuationScheduler.cancel(run.id)
            }
        }
    }

    private fun AutomationFlowStep.remainingWaitMillis(waitingStep: AutomationStepRunRecord): Long {
        val waitStartedAt = waitingStep.completedAt ?: waitingStep.startedAt
        val elapsedMillis = clock() - waitStartedAt
        return (waitMillis - elapsedMillis).coerceAtLeast(0L)
    }
}
