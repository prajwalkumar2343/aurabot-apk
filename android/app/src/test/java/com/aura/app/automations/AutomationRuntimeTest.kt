package com.aura.app.automations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuntimeTest {
    @Test
    fun restoreTriggersImmediatelyDisarmsDisabledRules() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules)
        val geofence = repository.upsert(geofenceSpec())
        val schedule = repository.upsert(scheduleSpec())

        runtime.restoreTriggers()
        repository.setEnabled(geofence.id, false)
        repository.setEnabled(schedule.id, false)
        runtime.restoreTriggers()

        assertTrue(geofence.id in geofences.removedByRestore)
        assertTrue(schedule.id in schedules.cancelledByRestore)
        assertFalse(geofence.id in geofences.activeIds)
        assertFalse(schedule.id in schedules.activeIds)
    }

    @Test
    fun deleteAndRestoreImmediatelyCancelsDeletedRule() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules)
        val geofence = repository.upsert(geofenceSpec())
        val schedule = repository.upsert(scheduleSpec())

        runtime.restoreTriggers()
        runtime.deleteAndRestore(geofence.id)
        runtime.deleteAndRestore(schedule.id)

        assertTrue(geofence.id in geofences.removedExplicitly)
        assertTrue(schedule.id in schedules.cancelledExplicitly)
        assertFalse(geofence.id in geofences.activeIds)
        assertFalse(schedule.id in schedules.activeIds)
    }

    @Test
    fun restoreTriggersRearmsWaitingFlowContinuations() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { 1_000L })
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val run = repository.createRun(
            automationId = saved.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Waiting,
            message = "waiting"
        )
        repository.recordStep(
            runId = run.id,
            automationId = saved.id,
            step = waitStep,
            stepIndex = 0,
            status = AutomationRunStatus.Waiting,
            attempt = 1,
            message = "waiting"
        )

        runtime.restoreTriggers()

        assertEquals(5_000L, continuations.scheduled[run.id])
    }

    @Test
    fun restoreTriggersSchedulesRemainingWaitForWaitingFlowContinuations() = runTest {
        var now = 1_000L
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { now })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { now })
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val run = repository.createRun(
            automationId = saved.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Waiting,
            message = "waiting"
        )
        repository.recordStep(
            runId = run.id,
            automationId = saved.id,
            step = waitStep,
            stepIndex = 0,
            status = AutomationRunStatus.Waiting,
            attempt = 1,
            message = "waiting"
        )
        now = 3_500L

        runtime.restoreTriggers()

        assertEquals(2_500L, continuations.scheduled[run.id])
    }

    @Test
    fun restoreTriggersTerminalizesDisabledWaitingFlowContinuations() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { 1_000L })
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val run = waitingRun(repository, saved, waitStep)
        repository.setEnabled(saved.id, false)

        runtime.restoreTriggers()

        assertEquals(AutomationRunStatus.Skipped, repository.getRun(run.id)?.status)
        assertEquals("Automation is disabled", repository.getRun(run.id)?.message)
        assertTrue(run.id in continuations.cancelled)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun restoreTriggersTerminalizesMissingWaitingFlowContinuations() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { 1_000L })
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val run = waitingRun(repository, saved, waitStep)
        repository.delete(saved.id)

        runtime.restoreTriggers()

        assertEquals(AutomationRunStatus.Failed, repository.getRun(run.id)?.status)
        assertEquals("Automation not found", repository.getRun(run.id)?.message)
        assertTrue(run.id in continuations.cancelled)
    }

    @Test
    fun restoreTriggersLeavesCheckpointRunsWaitingWithoutRearmingAlarm() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { 1_000L })
        val saved = repository.upsert(checkpointFlowSpec())
        val checkpointStep = saved.flow?.steps?.first() ?: error("checkpoint step missing")
        val run = waitingRun(repository, saved, checkpointStep)

        runtime.restoreTriggers()

        assertEquals(AutomationRunStatus.Waiting, repository.getRun(run.id)?.status)
        assertTrue(run.id in continuations.cancelled)
        assertFalse(run.id in continuations.scheduled)
    }

    @Test
    fun restoreTriggersFailsInterruptedRunningRuns() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val geofences = RecordingGeofenceRegistrar()
        val schedules = RecordingScheduleScheduler()
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(repository, geofences, schedules, continuations, clock = { 1_000L })
        val saved = repository.upsert(waitFlowSpec())
        val run = repository.createRun(
            automationId = saved.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Running,
            message = "running"
        )

        runtime.restoreTriggers()

        assertEquals(AutomationRunStatus.Failed, repository.getRun(run.id)?.status)
        assertEquals("Automation run was interrupted before completion", repository.getRun(run.id)?.message)
        assertTrue(run.id in continuations.cancelled)
        assertEquals(null, repository.activeRun(saved.id))
    }

    private fun geofenceSpec() = AutomationSpec(
        id = "leave-work",
        name = "Leave work",
        trigger = AutomationTrigger(
            type = AutomationTriggerTypes.Geofence,
            geofence = GeofenceTrigger(placeName = "Work", latitude = 12.9716, longitude = 77.5946)
        ),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Left work"))
    )

    private fun scheduleSpec() = AutomationSpec(
        id = "daily-check",
        name = "Daily check",
        trigger = AutomationTrigger(
            type = AutomationTriggerTypes.Schedule,
            schedule = ScheduleTrigger(mode = "daily", localTime = "09:00")
        ),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Check in"))
    )

    private fun waitFlowSpec() = AutomationSpec(
        id = "wait-flow",
        name = "Wait flow",
        trigger = AutomationTrigger(type = AutomationTriggerTypes.Manual),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Fallback")),
        flow = AutomationFlow(
            steps = listOf(
                AutomationFlowStep(id = "wait", type = AutomationFlowStepTypes.Wait, waitMillis = 5_000L),
                AutomationFlowStep(
                    id = "notify",
                    type = AutomationFlowStepTypes.Action,
                    action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Done")
                )
            )
        )
    )

    private fun checkpointFlowSpec() = AutomationSpec(
        id = "checkpoint-flow",
        name = "Checkpoint flow",
        trigger = AutomationTrigger(type = AutomationTriggerTypes.Manual),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Fallback")),
        flow = AutomationFlow(
            steps = listOf(
                AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                AutomationFlowStep(
                    id = "notify",
                    type = AutomationFlowStepTypes.Action,
                    action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Done")
                )
            )
        )
    )

    private suspend fun waitingRun(
        repository: AutomationRepository,
        spec: AutomationSpec,
        step: AutomationFlowStep
    ): AutomationRunRecord {
        val run = repository.createRun(
            automationId = spec.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Waiting,
            message = "waiting"
        )
        repository.recordStep(
            runId = run.id,
            automationId = spec.id,
            step = step,
            stepIndex = 0,
            status = AutomationRunStatus.Waiting,
            attempt = 1,
            message = "waiting"
        )
        return run
    }
}

private class RecordingGeofenceRegistrar : AutomationGeofenceRegistrar {
    val activeIds = linkedSetOf<String>()
    val removedByRestore = mutableListOf<String>()
    val removedExplicitly = mutableListOf<String>()

    override suspend fun restore(automations: List<AutomationSpec>) {
        automations.map { it.id }.forEach { id ->
            removedByRestore += id
            activeIds.remove(id)
        }
        automations
            .filter { it.enabled && it.trigger.type == AutomationTriggerTypes.Geofence }
            .mapTo(activeIds) { it.id }
    }

    override suspend fun remove(automationId: String) {
        removedExplicitly += automationId
        activeIds.remove(automationId)
    }
}

private class RecordingScheduleScheduler : AutomationScheduleScheduler {
    val activeIds = linkedSetOf<String>()
    val cancelledByRestore = mutableListOf<String>()
    val cancelledExplicitly = mutableListOf<String>()

    override fun restore(automations: List<AutomationSpec>) {
        automations.map { it.id }.forEach { id ->
            cancelledByRestore += id
            activeIds.remove(id)
        }
        automations
            .filter { it.enabled && it.trigger.type == AutomationTriggerTypes.Schedule }
            .mapTo(activeIds) { it.id }
    }

    override fun cancel(automationId: String) {
        cancelledExplicitly += automationId
        activeIds.remove(automationId)
    }
}

private class RecordingRuntimeFlowContinuationScheduler : AutomationFlowContinuationScheduler {
    val scheduled = linkedMapOf<String, Long>()
    val cancelled = linkedSetOf<String>()

    override fun schedule(runId: String, delayMillis: Long) {
        scheduled[runId] = delayMillis
    }

    override fun cancel(runId: String) {
        cancelled += runId
    }
}

private class RuntimeFakeAutomationDao : AutomationDao {
    private val automations = linkedMapOf<String, AutomationEntity>()
    private val logs = mutableListOf<AutomationRunLogEntity>()

    override suspend fun listAutomations(): List<AutomationEntity> =
        automations.values.sortedByDescending { it.updatedAt }

    override suspend fun listEnabledAutomations(): List<AutomationEntity> =
        listAutomations().filter { it.enabled }

    override suspend fun automation(id: String): AutomationEntity? = automations[id]

    override suspend fun upsertAutomation(entity: AutomationEntity) {
        automations[entity.id] = entity
    }

    override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
        automations[id]?.let { automations[id] = it.copy(enabled = enabled, updatedAt = updatedAt) }
    }

    override suspend fun markTriggered(id: String, triggeredAt: Long, updatedAt: Long) {
        automations[id]?.let { automations[id] = it.copy(lastTriggeredAt = triggeredAt, updatedAt = updatedAt) }
    }

    override suspend fun deleteAutomation(id: String) {
        automations.remove(id)
    }

    override suspend fun insertRunLog(entity: AutomationRunLogEntity) {
        logs += entity
    }

    override suspend fun runLogs(automationId: String, limit: Int): List<AutomationRunLogEntity> =
        logs.filter { it.automationId == automationId }.sortedByDescending { it.createdAt }.take(limit)

    private val runs = linkedMapOf<String, AutomationRunEntity>()
    private val stepRuns = mutableListOf<AutomationStepRunEntity>()

    override suspend fun upsertRun(entity: AutomationRunEntity) {
        runs[entity.id] = entity
    }

    override suspend fun run(id: String): AutomationRunEntity? = runs[id]

    override suspend fun activeRun(automationId: String): AutomationRunEntity? =
        runs.values
            .filter {
                it.automationId == automationId &&
                    it.status in setOf(AutomationRunStatus.Running, AutomationRunStatus.Waiting)
            }
            .maxByOrNull { it.updatedAt }

    override suspend fun activeRuns(): List<AutomationRunEntity> =
        runs.values
            .filter { it.status in setOf(AutomationRunStatus.Running, AutomationRunStatus.Waiting) }
            .sortedByDescending { it.updatedAt }

    override suspend fun runs(automationId: String, limit: Int): List<AutomationRunEntity> =
        runs.values.filter { it.automationId == automationId }.sortedByDescending { it.updatedAt }.take(limit)

    override suspend fun insertStepRun(entity: AutomationStepRunEntity) {
        stepRuns += entity
    }

    override suspend fun stepRuns(runId: String): List<AutomationStepRunEntity> =
        stepRuns.filter { it.runId == runId }.sortedWith(compareBy<AutomationStepRunEntity> { it.stepIndex }.thenBy { it.attempt })
}
