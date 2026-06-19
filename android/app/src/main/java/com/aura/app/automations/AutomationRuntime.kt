package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AutomationRuntime(
    private val repository: AutomationRepository,
    private val geofenceRegistrar: AutomationGeofenceRegistrar,
    private val scheduleScheduler: AutomationScheduleScheduler,
    private val flowContinuationScheduler: AutomationFlowContinuationScheduler = NoOpAutomationFlowContinuationScheduler,
    private val executionRegistry: AutomationExecutionRegistry = AutomationExecutionRegistry(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val restoreMutex = Mutex()

    suspend fun restoreTriggers() = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            restoreTriggersLocked()
        }
    }

    suspend fun upsertAndRestore(spec: AutomationSpec): AutomationSpec = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            val previous = spec.id.takeIf { it.isNotBlank() }?.let { repository.get(it) }
            if (previous == null) {
                saveAndRestore(spec, previous)
            } else {
                executionRegistry.mutate(
                    previous.id,
                    AutomationRunStatus.Failed,
                    "Automation changed while run was active"
                ) {
                    saveAndRestore(spec, previous)
                }
            }
        }
    }

    suspend fun setEnabledAndRestore(id: String, enabled: Boolean): AutomationSpec = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            val previous = repository.get(id)
                ?: throw IllegalArgumentException("Automation not found")
            if (previous.enabled == enabled) return@withLock previous
            executionRegistry.mutate(
                id,
                if (enabled) AutomationRunStatus.Failed else AutomationRunStatus.Skipped,
                if (enabled) "Automation changed while run was active" else "Automation was disabled while run was active"
            ) {
                repository.setEnabled(id, enabled)
                val saved = repository.get(id)
                    ?: throw IllegalStateException("Automation disappeared after update")
                restoreConfigurationOrRollback(saved, previous)
                saved
            }
        }
    }

    suspend fun deleteAndRestore(id: String) = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            executionRegistry.mutate(
                id,
                AutomationRunStatus.Failed,
                "Automation was deleted while run was active"
            ) {
                repository.activeRuns(id).forEach { run ->
                    runCatching { flowContinuationScheduler.cancel(run.id) }
                }
                repository.delete(id)
                runCatching { geofenceRegistrar.remove(id) }
                runCatching { scheduleScheduler.cancel(id) }
                restoreTriggersLocked()
            }
        }
    }

    private suspend fun saveAndRestore(spec: AutomationSpec, previous: AutomationSpec?): AutomationSpec {
        val saved = repository.upsert(spec)
        restoreConfigurationOrRollback(saved, previous)
        return saved
    }

    private suspend fun restoreTriggersLocked() {
        val automations = repository.list()
        val failures = restoreConfiguredTriggerFailures(automations)
        captureFailure { restoreFlowContinuations(automations) }?.let(failures::add)
        failures.throwIfNotEmpty()
    }

    private suspend fun restoreConfigurationOrRollback(
        saved: AutomationSpec,
        previous: AutomationSpec?
    ) {
        val registrationFailures = try {
            restoreConfiguredTriggerFailures(repository.list())
        } catch (error: CancellationException) {
            val rollbackFailures = withContext(NonCancellable) {
                rollbackConfiguration(saved.id, previous)
            }
            rollbackFailures.forEach(error::addSuppressed)
            throw error
        }
        if (registrationFailures.isNotEmpty()) {
            val rollbackFailures = withContext(NonCancellable) {
                rollbackConfiguration(saved.id, previous)
            }
            throw AutomationConfigurationException(registrationFailures, rollbackFailures)
        }
        restoreFlowContinuations(repository.list())
    }

    private suspend fun rollbackConfiguration(
        savedId: String,
        previous: AutomationSpec?
    ): List<Exception> {
        val failures = mutableListOf<Exception>()
        captureCleanupFailure {
            if (previous == null) repository.delete(savedId) else repository.upsert(previous)
        }?.let(failures::add)
        if (previous == null) {
            captureCleanupFailure { geofenceRegistrar.remove(savedId) }?.let(failures::add)
            captureCleanupFailure { scheduleScheduler.cancel(savedId) }?.let(failures::add)
        }
        val automations = try {
            repository.list()
        } catch (error: Exception) {
            failures += error
            return failures
        }
        captureCleanupFailure { geofenceRegistrar.restore(automations) }?.let(failures::add)
        captureCleanupFailure { scheduleScheduler.restore(automations) }?.let(failures::add)
        return failures
    }

    private suspend fun restoreConfiguredTriggerFailures(
        automations: List<AutomationSpec>
    ): MutableList<Exception> = mutableListOf<Exception>().apply {
        captureFailure { geofenceRegistrar.restore(automations) }?.let(::add)
        captureFailure { scheduleScheduler.restore(automations) }?.let(::add)
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
            terminalizeInterruptedRun(run)
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

    private suspend fun terminalizeInterruptedRun(run: AutomationRunRecord) {
        val message = "Automation run was interrupted before completion"
        executionRegistry.mutate(run.automationId, AutomationRunStatus.Failed, message) {
            repository.getRun(run.id)
                ?.takeIf { it.status == AutomationRunStatus.Running }
                ?.let { current -> terminalizeRun(current, AutomationRunStatus.Failed, message) }
        }
    }

    private suspend fun terminalizeRun(run: AutomationRunRecord, status: String, message: String) {
        repository.updateRun(run.id, status, message)
        flowContinuationScheduler.cancel(run.id)
        repository.log(run.automationId, run.eventType, status, message)
    }

    private fun AutomationFlowStep.remainingWaitMillis(waitingStep: AutomationStepRunRecord): Long {
        val waitStartedAt = waitingStep.completedAt ?: waitingStep.startedAt
        val now = clock()
        val elapsedMillis = if (waitStartedAt >= 0L && now >= waitStartedAt) {
            now - waitStartedAt
        } else {
            0L
        }
        val configuredWait = waitMillis.coerceAtLeast(0L)
        return (configuredWait - elapsedMillis).coerceIn(0L, configuredWait)
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

    private suspend fun captureCleanupFailure(block: suspend () -> Unit): Exception? =
        try {
            block()
            null
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

internal class AutomationConfigurationException(
    registrationFailures: List<Exception>,
    rollbackFailures: List<Exception>
) : Exception(
    buildString {
        append("Automation trigger registration failed: ")
        append(registrationFailures.failureMessages())
        if (rollbackFailures.isEmpty()) {
            append(". The previous configuration was restored")
        } else {
            append(". Configuration rollback also failed: ")
            append(rollbackFailures.failureMessages())
        }
    },
    registrationFailures.firstOrNull()
) {
    init {
        (registrationFailures.drop(1) + rollbackFailures).forEach(::addSuppressed)
    }
}

private fun List<Exception>.failureMessages(): String =
    joinToString("; ") { it.message ?: it::class.simpleName ?: "Unknown error" }
