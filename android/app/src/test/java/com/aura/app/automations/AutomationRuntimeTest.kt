package com.aura.app.automations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRuntimeTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoreTriggersSerializesConcurrentRestores() = runTest {
        val dao = RuntimeFakeAutomationDao()
        val firstListStarted = CompletableDeferred<Unit>()
        val releaseFirstList = CompletableDeferred<Unit>()
        dao.listStarted = firstListStarted
        dao.listGate = releaseFirstList
        val runtime = AutomationRuntime(
            AutomationRepository(dao, clock = { 1_000L }),
            RecordingGeofenceRegistrar(),
            RecordingScheduleScheduler()
        )

        val first = async { runtime.restoreTriggers() }
        firstListStarted.await()
        val second = async { runtime.restoreTriggers() }
        runCurrent()

        assertEquals(1, dao.maxConcurrentListCalls)
        releaseFirstList.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, dao.maxConcurrentListCalls)
        assertEquals(2, dao.totalListCalls)
    }

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
    fun deleteAndRestoreCancelsAllParallelRunsAndDeletesHistory() = runTest {
        var now = 1_000L
        val dao = RuntimeFakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { now })
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(
            repository,
            RecordingGeofenceRegistrar(),
            RecordingScheduleScheduler(),
            continuations
        )
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val runs = (1..2).map {
            now += 100L
            repository.createRun(
                automationId = saved.id,
                eventType = AutomationEvents.Manual,
                values = emptyMap(),
                status = AutomationRunStatus.Waiting,
                message = "waiting"
            ).also { run ->
                repository.recordStep(
                    runId = run.id,
                    automationId = saved.id,
                    step = waitStep,
                    stepIndex = 0,
                    status = AutomationRunStatus.Waiting,
                    attempt = 1,
                    message = "waiting"
                )
            }
        }
        repository.log(saved.id, AutomationEvents.Manual, AutomationRunStatus.Waiting, "waiting")
        continuations.failOnCancel = runs.first().id

        runtime.deleteAndRestore(saved.id)

        assertEquals(runs.map { it.id }.toSet(), continuations.cancelled)
        assertEquals(null, repository.get(saved.id))
        assertTrue(repository.runs(saved.id).isEmpty())
        assertTrue(repository.logs(saved.id).isEmpty())
        runs.forEach { run ->
            assertEquals(null, repository.getRun(run.id))
            assertTrue(repository.stepRuns(run.id).isEmpty())
        }
    }

    @Test
    fun repositoryBoundsHistoryWithoutPruningActiveRuns() = runTest {
        var now = 1_000L
        val repository = AutomationRepository(
            RuntimeFakeAutomationDao(),
            clock = { now },
            runHistoryLimit = 2,
            logHistoryLimit = 2
        )
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val activeRun = repository.createRun(
            automationId = saved.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Waiting,
            message = "waiting"
        )
        val terminalRuns = (1..4).map { attempt ->
            now += 100L
            repository.createRun(saved.id, AutomationEvents.Manual, emptyMap()).also { run ->
                repository.recordStep(
                    runId = run.id,
                    automationId = saved.id,
                    step = waitStep,
                    stepIndex = 0,
                    status = AutomationRunStatus.Success,
                    attempt = attempt,
                    message = "completed"
                )
                now += 1L
                repository.updateRun(run.id, AutomationRunStatus.Success, "completed")
            }
        }
        listOf("one", "two", "three").forEach { message ->
            now += 100L
            repository.log(saved.id, AutomationEvents.Manual, AutomationRunStatus.Success, message)
        }

        val retainedRuns = repository.runs(saved.id, limit = 20)
        assertEquals(3, retainedRuns.size)
        assertTrue(retainedRuns.any { it.id == activeRun.id })
        assertEquals(terminalRuns.takeLast(2).map { it.id }.toSet(), retainedRuns.filter { it.completedAt != null }.map { it.id }.toSet())
        terminalRuns.take(2).forEach { run ->
            assertEquals(null, repository.getRun(run.id))
            assertTrue(repository.stepRuns(run.id).isEmpty())
        }
        assertEquals(listOf("three", "two"), repository.logs(saved.id).map { it.message })
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
    fun restoreTriggersTerminalizesWaitingRunWhenAutomationChanged() = runTest {
        val repository = AutomationRepository(RuntimeFakeAutomationDao(), clock = { 1_000L })
        val continuations = RecordingRuntimeFlowContinuationScheduler()
        val runtime = AutomationRuntime(
            repository,
            RecordingGeofenceRegistrar(),
            RecordingScheduleScheduler(),
            continuations,
            clock = { 1_000L }
        )
        val saved = repository.upsert(waitFlowSpec())
        val waitStep = saved.flow?.steps?.first() ?: error("wait step missing")
        val run = waitingRun(repository, saved, waitStep)
        repository.upsert(
            saved.copy(
                flow = saved.flow?.copy(
                    steps = saved.flow.steps.map { step ->
                        if (step.id == "wait") step.copy(waitMillis = 10_000L) else step
                    }
                )
            )
        )

        runtime.restoreTriggers()

        assertEquals(AutomationRunStatus.Failed, repository.getRun(run.id)?.status)
        assertEquals("Automation changed while run was waiting", repository.getRun(run.id)?.message)
        assertTrue(run.id in continuations.cancelled)
        assertFalse(run.id in continuations.scheduled)
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
        dao.deleteAutomation(saved.id)

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
    var failOnCancel: String? = null

    override fun schedule(runId: String, delayMillis: Long) {
        scheduled[runId] = delayMillis
    }

    override fun cancel(runId: String) {
        cancelled += runId
        if (runId == failOnCancel) error("alarm service unavailable")
    }
}

private class RuntimeFakeAutomationDao : AutomationDao {
    private val automations = linkedMapOf<String, AutomationEntity>()
    private val logs = mutableListOf<AutomationRunLogEntity>()
    var listStarted: CompletableDeferred<Unit>? = null
    var listGate: CompletableDeferred<Unit>? = null
    var maxConcurrentListCalls = 0
        private set
    var totalListCalls = 0
        private set
    private var concurrentListCalls = 0

    override suspend fun listAutomations(): List<AutomationEntity> {
        totalListCalls += 1
        concurrentListCalls += 1
        maxConcurrentListCalls = maxOf(maxConcurrentListCalls, concurrentListCalls)
        listStarted?.complete(Unit)
        return try {
            listGate?.await()
            automations.values.sortedByDescending { it.updatedAt }
        } finally {
            concurrentListCalls -= 1
        }
    }

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

    override suspend fun deleteRunLogs(automationId: String) {
        logs.removeAll { it.automationId == automationId }
    }

    override suspend fun deleteStepRuns(automationId: String) {
        stepRuns.removeAll { it.automationId == automationId }
    }

    override suspend fun deleteRuns(automationId: String) {
        runs.entries.removeAll { it.value.automationId == automationId }
    }

    override suspend fun insertRunLog(entity: AutomationRunLogEntity) {
        logs += entity
    }

    override suspend fun runLogs(automationId: String, limit: Int): List<AutomationRunLogEntity> =
        logs.filter { it.automationId == automationId }.sortedByDescending { it.createdAt }.take(limit)

    override suspend fun pruneRunLogs(automationId: String, retainCount: Int) {
        val retainedIds = logs
            .filter { it.automationId == automationId }
            .sortedWith(compareByDescending<AutomationRunLogEntity> { it.createdAt }.thenByDescending { it.id })
            .take(retainCount)
            .mapTo(mutableSetOf()) { it.id }
        logs.removeAll { it.automationId == automationId && it.id !in retainedIds }
    }

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

    override suspend fun activeRuns(automationId: String): List<AutomationRunEntity> =
        activeRuns().filter { it.automationId == automationId }

    override suspend fun runs(automationId: String, limit: Int): List<AutomationRunEntity> =
        runs.values.filter { it.automationId == automationId }.sortedByDescending { it.updatedAt }.take(limit)

    override suspend fun insertStepRun(entity: AutomationStepRunEntity) {
        stepRuns += entity
    }

    override suspend fun stepRuns(runId: String): List<AutomationStepRunEntity> =
        stepRuns.filter { it.runId == runId }.sortedWith(compareBy<AutomationStepRunEntity> { it.stepIndex }.thenBy { it.attempt })

    override suspend fun pruneStepRuns(automationId: String, retainCount: Int) {
        val prunedRunIds = terminalRunsToPrune(automationId, retainCount).mapTo(mutableSetOf()) { it.id }
        stepRuns.removeAll { it.runId in prunedRunIds }
    }

    override suspend fun pruneRuns(automationId: String, retainCount: Int) {
        terminalRunsToPrune(automationId, retainCount).forEach { runs.remove(it.id) }
    }

    private fun terminalRunsToPrune(automationId: String, retainCount: Int): List<AutomationRunEntity> =
        runs.values
            .filter {
                it.automationId == automationId &&
                    it.status !in setOf(AutomationRunStatus.Running, AutomationRunStatus.Waiting)
            }
            .sortedWith(compareByDescending<AutomationRunEntity> { it.updatedAt }.thenByDescending { it.id })
            .drop(retainCount)
}
