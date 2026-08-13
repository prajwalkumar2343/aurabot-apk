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
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val checkpointNotifier: AutomationCheckpointNotifier = NoOpAutomationCheckpointNotifier
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
                saveAndRestore(spec, previous, mutationBarrierHeld = false)
            } else {
                executionRegistry.mutate(
                    previous.id,
                    AutomationRunStatus.Failed,
                    "Automation changed while run was active"
                ) {
                    saveAndRestore(spec, previous, mutationBarrierHeld = true)
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
                restoreConfigurationOrRollback(saved, previous, mutationBarrierHeld = true)
                saved
            }
        }
    }

    suspend fun deleteAndRestore(id: String) = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            val cleanupFailures = mutableListOf<Exception>()
            var reconciliationRequired = false
            var primaryFailure: Throwable? = null
            try {
                executionRegistry.mutate(
                    id,
                    AutomationRunStatus.Failed,
                    "Automation was deleted while run was active"
                ) {
                    val activeRuns = repository.activeRuns(id)
                    reconciliationRequired = true
                    withContext(NonCancellable) {
                        activeRuns.forEach { run ->
                            captureCleanupFailure { flowContinuationScheduler.cancel(run.id) }
                                ?.let(cleanupFailures::add)
                            captureCleanupFailure { checkpointNotifier.cancel(run.id) }
                                ?.let(cleanupFailures::add)
                        }
                        repository.delete(id)
                        captureCleanupFailure { geofenceRegistrar.remove(id) }?.let(cleanupFailures::add)
                        captureCleanupFailure { scheduleScheduler.cancel(id) }?.let(cleanupFailures::add)
                    }
                }
            } catch (error: Throwable) {
                primaryFailure = error
            }
            if (reconciliationRequired) {
                withContext(NonCancellable) {
                    captureCleanupFailure { restoreTriggersLocked() }?.let(cleanupFailures::add)
                }
            }
            primaryFailure?.let { error ->
                when (error) {
                    is CancellationException -> {
                        cleanupFailures.forEach(error::addSuppressed)
                        throw error
                    }
                    is Exception -> throw AutomationDeletionException(error, cleanupFailures)
                    else -> {
                        cleanupFailures.forEach(error::addSuppressed)
                        throw error
                    }
                }
            }
            cleanupFailures.throwDeletionFailures()
        }
    }

    private suspend fun saveAndRestore(
        spec: AutomationSpec,
        previous: AutomationSpec?,
        mutationBarrierHeld: Boolean
    ): AutomationSpec {
        val saved = repository.upsert(spec)
        restoreConfigurationOrRollback(saved, previous, mutationBarrierHeld)
        return saved
    }

    private suspend fun restoreTriggersLocked() {
        val retirement = repository.retireLegacyCrossAppAutomations()
        val failures = retireLegacyAutomations(retirement.retiredIds)
        repository.failInterruptedEvents()
        val automations = retirement.automations
        failures += restoreConfiguredTriggerFailures(automations)
        captureFailure { restoreFlowContinuations(automations) }?.let(failures::add)
        failures.throwIfNotEmpty()
    }

    private suspend fun retireLegacyAutomations(retiredIds: List<String>): MutableList<Exception> {
        val failures = mutableListOf<Exception>()
        retiredIds.forEach { automationId ->
            repository.activeRuns(automationId).forEach { run ->
                captureFailure { flowContinuationScheduler.cancel(run.id) }?.let(failures::add)
                captureFailure { checkpointNotifier.cancel(run.id) }?.let(failures::add)
                captureFailure {
                    repository.updateRun(
                        run.id,
                        AutomationRunStatus.Skipped,
                        "Cross-app UI automation was removed; this run was not resumed"
                    )
                }?.let(failures::add)
                captureFailure {
                    repository.log(
                        automationId,
                        run.eventType,
                        AutomationRunStatus.Skipped,
                        "Cross-app UI automation was removed; this run was not resumed"
                    )
                }?.let(failures::add)
            }
            captureFailure { geofenceRegistrar.remove(automationId) }?.let(failures::add)
            captureFailure { scheduleScheduler.cancel(automationId) }?.let(failures::add)
        }
        return failures
    }

    private suspend fun restoreConfigurationOrRollback(
        saved: AutomationSpec,
        previous: AutomationSpec?,
        mutationBarrierHeld: Boolean
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
        restoreFlowContinuations(
            automations = repository.list(),
            onlyAutomationId = saved.id,
            mutationBarrierHeldFor = saved.id.takeIf { mutationBarrierHeld }
        )
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

    private suspend fun restoreFlowContinuations(
        automations: List<AutomationSpec>,
        onlyAutomationId: String? = null,
        mutationBarrierHeldFor: String? = null
    ) {
        val automationById = automations.associateBy { it.id }
        val failures = mutableListOf<Exception>()
        repository.activeRuns()
            .filter { onlyAutomationId == null || it.automationId == onlyAutomationId }
            .forEach { run ->
                captureFailure {
                    restoreFlowContinuation(run, automationById, mutationBarrierHeldFor)
                }?.let(failures::add)
            }
        failures.throwIfNotEmpty()
    }

    private suspend fun restoreFlowContinuation(
        run: AutomationRunRecord,
        automationById: Map<String, AutomationSpec>,
        mutationBarrierHeldFor: String?
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
            terminalizeInterruptedRun(run, mutationBarrierHeldFor == run.automationId)
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
            AutomationFlowStepTypes.Checkpoint -> {
                flowContinuationScheduler.cancel(run.id)
                val checkpointStartedAt = waitingStep.completedAt ?: waitingStep.startedAt
                val expiresAt = AutomationCheckpointPolicy.expiresAt(checkpointStartedAt, flowStep.metadata)
                if (clock() > expiresAt) {
                    terminalizeRun(run, AutomationRunStatus.Failed, "Automation approval expired")
                } else {
                    checkpointNotifier.present(
                        AutomationCheckpointRequest(
                            runId = run.id,
                            automationName = spec.name,
                            message = AutomationCheckpointPolicy.approvalMessage(spec, waitingStep.stepIndex),
                            expiresAt = expiresAt
                        )
                    )
                }
            }
            else -> terminalizeRun(
                run,
                AutomationRunStatus.Failed,
                "Automation run waiting step is no longer resumable"
            )
        }
    }

    private suspend fun terminalizeInterruptedRun(run: AutomationRunRecord, mutationBarrierHeld: Boolean) {
        val spec = repository.get(run.automationId)
        val unsettledStep = repository.stepRuns(run.id)
            .lastOrNull { it.status == AutomationRunStatus.Running && it.completedAt == null }
        val ambiguousSideEffect = unsettledStep != null && spec
            ?.effectiveSteps()
            ?.getOrNull(unsettledStep.stepIndex)
            ?.action
            ?.hasAtMostOnceSideEffect() == true
        val terminalStatus = if (ambiguousSideEffect) {
            AutomationRunStatus.OutcomeUnknown
        } else {
            AutomationRunStatus.Failed
        }
        val message = if (ambiguousSideEffect) {
            "Automation stopped during an irreversible action; verify the external result before retrying"
        } else {
            "Automation run was interrupted before completion"
        }
        val terminalizeIfStillRunning: suspend () -> Unit = {
            repository.getRun(run.id)
                ?.takeIf { it.status == AutomationRunStatus.Running }
                ?.let { current -> terminalizeRun(current, terminalStatus, message) }
        }
        if (mutationBarrierHeld) {
            terminalizeIfStillRunning()
        } else {
            executionRegistry.mutate(run.automationId, terminalStatus, message) {
                terminalizeIfStillRunning()
            }
        }
    }

    private suspend fun terminalizeRun(run: AutomationRunRecord, status: String, message: String) {
        repository.updateRun(run.id, status, message)
        flowContinuationScheduler.cancel(run.id)
        checkpointNotifier.cancel(run.id)
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

    private fun List<Exception>.throwDeletionFailures() {
        if (isEmpty()) return
        val cancellation = filterIsInstance<CancellationException>().firstOrNull()
        if (cancellation != null) {
            filter { it !== cancellation }.forEach(cancellation::addSuppressed)
            throw cancellation
        }
        throw AutomationDeletionException(this)
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

internal class AutomationDeletionException(
    primaryFailure: Exception?,
    failures: List<Exception>
) : Exception(
    if (primaryFailure == null) {
        failures.joinToString(
            prefix = "Automation was deleted, but cleanup failed: ",
            separator = "; "
        ) { it.message ?: it::class.simpleName ?: "Unknown error" }
    } else {
        "Automation deletion failed: ${primaryFailure.message ?: primaryFailure::class.simpleName ?: "Unknown error"}"
    },
    primaryFailure ?: failures.firstOrNull()
) {
    init {
        if (primaryFailure == null) failures.drop(1).forEach(::addSuppressed) else failures.forEach(::addSuppressed)
    }

    constructor(failures: List<Exception>) : this(null, failures)
}

private fun List<Exception>.failureMessages(): String =
    joinToString("; ") { it.message ?: it::class.simpleName ?: "Unknown error" }
