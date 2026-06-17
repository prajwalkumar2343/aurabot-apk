package com.aura.app.automations

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutomationEngineTest {
    @Test
    fun geofenceExitAutomationRunsMessageActionWithEtaContext() = runTest {
        var now = 1_000L
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val spec = leaveWorkSpec()

        val saved = repository.upsert(spec)
        val results = engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                occurredAt = now,
                values = mapOf("latitude" to "12.9716", "longitude" to "77.5946")
            )
        )

        assertEquals(1, results.size)
        assertEquals(AutomationRunStatus.Success, results.first().status)
        assertEquals(1, executor.events.size)
        assertEquals("Work", executor.events.first().values["placeName"])
        assertTrue(executor.events.first().values["etaMinutes"]?.toIntOrNull() ?: 0 > 0)
        assertEquals("local_distance", executor.events.first().values["etaProvider"])
        assertEquals("estimated", executor.events.first().values["etaConfidence"])
    }

    @Test
    fun etaContextUsesInjectedProvider() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            contextEnricher = DefaultAutomationContextEnricher(FixedEtaProvider()),
            actionExecutor = executor,
            clock = { 1_000L }
        )
        val saved = repository.upsert(leaveWorkSpec())

        engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                values = mapOf("latitude" to "12.9716", "longitude" to "77.5946")
            )
        )

        assertEquals("17", executor.events.first().values["etaMinutes"])
        assertEquals("fake_routes", executor.events.first().values["etaProvider"])
    }

    @Test
    fun cooldownSkipsRepeatedRuns() = runTest {
        var now = 1_000L
        val repository = AutomationRepository(FakeAutomationDao(), clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val saved = repository.upsert(leaveWorkSpec().copy(cooldownMillis = 60_000L))

        engine.handle(AutomationEvent(type = AutomationEvents.GeofenceExit, automationId = saved.id, occurredAt = now))
        now += 1_000L
        val results = engine.handle(AutomationEvent(type = AutomationEvents.GeofenceExit, automationId = saved.id, occurredAt = now))

        assertEquals(AutomationRunStatus.Skipped, results.first().status)
        assertEquals(1, executor.events.size)
    }

    @Test
    fun validatorForcesMessageConfirmation() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val saved = repository.upsert(
            leaveWorkSpec().copy(
                actions = listOf(leaveWorkSpec().actions.first().copy(requireConfirmation = false))
            )
        )

        assertEquals(true, saved.actions.first().requireConfirmation)
    }

    @Test
    fun directSmsRequiresRecipientAndSmsPermission() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val directSms = leaveWorkSpec().copy(
            actions = listOf(
                AutomationAction(
                    type = AutomationActionTypes.DirectSms,
                    messageTemplate = "I left {{placeName}}.",
                    recipientAddress = "+15555550123",
                    requireConfirmation = false
                )
            )
        )

        val saved = repository.upsert(directSms)
        val permissions = AutomationPermissionPlanner().requiredPermissions(saved)

        assertEquals(false, saved.actions.first().requireConfirmation)
        assertTrue(permissions.any { it.endsWith("SEND_SMS") })
    }

    @Test
    fun crossAppFlowActionsRequireAccessibilityService() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "tap-login",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(
                                type = AutomationActionTypes.TapText,
                                metadata = mapOf(AutomationActionMetadata.Text to "Log in")
                            )
                        )
                    )
                )
            )
        )

        val permissions = AutomationPermissionPlanner().requiredPermissions(saved)

        assertTrue(AutomationPermissionPlanner.AccessibilityService in permissions)
    }

    @Test
    fun actionTypeSetsClassifyCrossAppAndMessageActions() {
        assertTrue(AutomationActionTypes.WaitForIdle in AutomationActionTypeSets.CrossApp)
        assertTrue(AutomationActionTypes.DirectSms in AutomationActionTypeSets.Message)
        assertTrue(AutomationActionTypes.Notify in AutomationActionTypeSets.All)
        assertTrue(AutomationActionTypeSets.CrossApp.all { it in AutomationActionTypeSets.All })
        assertTrue(AutomationActionTypeSets.Message.all { it in AutomationActionTypeSets.All })
    }

    @Test
    fun validatorRejectsCrossAppActionsWithoutTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                manualSpec().copy(
                    actions = listOf(AutomationAction(type = AutomationActionTypes.OpenApp))
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                manualSpec().copy(
                    actions = listOf(AutomationAction(type = AutomationActionTypes.TapText))
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                manualSpec().copy(
                    actions = listOf(
                        AutomationAction(
                            type = AutomationActionTypes.TapBounds,
                            metadata = mapOf(
                                AutomationActionMetadata.BoundsLeft to "0",
                                AutomationActionMetadata.BoundsTop to "0"
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun validatorRejectsInvalidCrossAppMetadataValues() {
        fun invalid(metadata: Map<String, String>) {
            assertThrows(IllegalArgumentException::class.java) {
                AutomationValidator.validate(
                    manualSpec().copy(
                        actions = listOf(
                            AutomationAction(
                                type = AutomationActionTypes.TapTarget,
                                metadata = mapOf(AutomationActionMetadata.Text to "Continue") + metadata
                            )
                        )
                    )
                )
            }
        }

        invalid(mapOf(AutomationActionMetadata.TimeoutMillis to "100"))
        invalid(mapOf(AutomationActionMetadata.PartialMatch to "yes"))
        invalid(mapOf(AutomationActionMetadata.Occurrence to "-1"))
        invalid(mapOf(AutomationActionMetadata.DiagnosticMaxNodes to "0"))
        invalid(mapOf(AutomationActionMetadata.StableSamples to "1"))
        invalid(mapOf(AutomationActionMetadata.RiskLevel to "urgent"))
    }

    @Test
    fun validatorRequiresCheckpointBeforeHighImpactCrossAppGesture() {
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                manualSpec().copy(
                    actions = listOf(
                        AutomationAction(
                            type = AutomationActionTypes.TapTarget,
                            metadata = mapOf(AutomationActionMetadata.Text to "Send")
                        )
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                manualSpec().copy(
                    actions = emptyList(),
                    flow = AutomationFlow(
                        steps = listOf(
                            AutomationFlowStep(
                                id = "send",
                                type = AutomationFlowStepTypes.Action,
                                action = AutomationAction(
                                    type = AutomationActionTypes.TapTarget,
                                    metadata = mapOf(AutomationActionMetadata.Text to "Send")
                                )
                            ),
                            AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint)
                        )
                    )
                )
            )
        }

        val validated = AutomationValidator.validate(
            manualSpec().copy(
                actions = emptyList(),
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "send",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(
                                type = AutomationActionTypes.TapTarget,
                                metadata = mapOf(AutomationActionMetadata.Text to "Send")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(listOf("confirm", "send"), validated.flow?.steps?.map { it.id })
    }

    @Test
    fun validatorRejectsAmbiguousFlowStepShapes() {
        fun invalid(step: AutomationFlowStep) {
            assertThrows(IllegalArgumentException::class.java) {
                AutomationValidator.validate(
                    manualSpec().copy(
                        actions = emptyList(),
                        flow = AutomationFlow(steps = listOf(step))
                    )
                )
            }
        }

        invalid(
            AutomationFlowStep(
                id = "wait-with-action",
                type = AutomationFlowStepTypes.Wait,
                waitMillis = 1_000L,
                action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ignored")
            )
        )
        invalid(
            AutomationFlowStep(
                id = "action-with-wait",
                type = AutomationFlowStepTypes.Action,
                waitMillis = 1_000L,
                action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Run")
            )
        )
        invalid(
            AutomationFlowStep(
                id = "checkpoint-with-condition",
                type = AutomationFlowStepTypes.Checkpoint,
                condition = AutomationCondition(key = "ready")
            )
        )
    }

    @Test
    fun dailyScheduleHonorsDaysOfWeek() {
        val zone = ZoneId.of("UTC")
        val mondayAfterRunTime = ZonedDateTime.of(2026, 6, 8, 10, 0, 0, 0, zone)
        val trigger = ScheduleTrigger(
            mode = "daily",
            localTime = "09:00",
            daysOfWeek = listOf(3)
        )

        val next = ScheduleAutomationScheduler.nextTriggerAt(trigger, mondayAfterRunTime)

        assertEquals(
            ZonedDateTime.of(2026, 6, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            next
        )
    }

    @Test
    fun validatorRejectsInvalidDailyScheduleTimesAndDays() {
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                scheduleSpec().copy(
                    trigger = AutomationTrigger(
                        type = AutomationTriggerTypes.Schedule,
                        schedule = ScheduleTrigger(mode = "daily", localTime = "99:99")
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
                scheduleSpec().copy(
                    trigger = AutomationTrigger(
                        type = AutomationTriggerTypes.Schedule,
                        schedule = ScheduleTrigger(mode = "daily", localTime = "09:00", daysOfWeek = listOf(0, 8))
                    )
                )
            )
        }
    }

    @Test
    fun manualTriggerDoesNotMatchDifferentEventTypeByIdOnly() {
        val matcher = AutomationTriggerMatcher()
        val spec = manualSpec()

        assertEquals(
            false,
            matcher.matches(
                spec,
                AutomationEvent(type = AutomationEvents.ScheduleTick, automationId = spec.id)
            )
        )
        assertEquals(
            true,
            matcher.matches(
                spec,
                AutomationEvent(type = AutomationEvents.Manual, automationId = spec.id)
            )
        )
    }

    @Test
    fun flowAutomationRunsStepsInOrderAndRecordsDurableRun() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "check-context",
                            type = AutomationFlowStepTypes.Condition,
                            condition = AutomationCondition(key = "ready", operator = AutomationOperators.Equals, value = "true")
                        ),
                        AutomationFlowStep(
                            id = "notify-user",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Done")
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id, mapOf("ready" to "true"))

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(listOf("check-context", "notify-user"), result.stepResults.map { it.stepId })
        assertEquals(1, executor.events.size)
        val runId = result.runId ?: error("runId missing")
        assertEquals(AutomationRunStatus.Success, repository.getRun(runId)?.status)
        assertEquals(2, repository.stepRuns(runId).size)
    }

    @Test
    fun flowAutomationPausesAndResumesAfterCheckpoint() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "confirm",
                            type = AutomationFlowStepTypes.Checkpoint,
                            metadata = mapOf("message" to "Waiting for confirmation")
                        ),
                        AutomationFlowStep(
                            id = "send",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Confirmed")
                        )
                    )
                )
            )
        )

        val waiting = engine.runNow(saved.id)
        val resumed = engine.resumeRun(waiting.runId ?: error("runId missing"))

        assertEquals(AutomationRunStatus.Waiting, waiting.status)
        assertEquals(AutomationRunStatus.Success, resumed.status)
        assertEquals(listOf("send"), resumed.stepResults.map { it.stepId })
        assertEquals(1, executor.events.size)
    }

    @Test
    fun manualRunSkipsWhenFlowAlreadyWaiting() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )

        val waiting = engine.runNow(saved.id)
        val duplicate = engine.runNow(saved.id)

        assertEquals(AutomationRunStatus.Waiting, waiting.status)
        assertEquals(AutomationRunStatus.Skipped, duplicate.status)
        assertEquals("Automation already has an active run", duplicate.message)
        assertEquals(waiting.runId, duplicate.runId)
        assertEquals(0, executor.events.size)
    }

    @Test
    fun resumeRunSkipsTerminalRunsWithoutReplayingActions() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "send",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Confirmed")
                        )
                    )
                )
            )
        )

        val waiting = engine.runNow(saved.id)
        val runId = waiting.runId ?: error("runId missing")
        val resumed = engine.resumeRun(runId)
        val duplicate = engine.resumeRun(runId)

        assertEquals(AutomationRunStatus.Success, resumed.status)
        assertEquals(AutomationRunStatus.Skipped, duplicate.status)
        assertEquals("Automation run is not waiting", duplicate.message)
        assertEquals(1, executor.events.size)
    }

    @Test
    fun resumeRunFailsWaitingRunsWithoutAWaitingStep() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val scheduler = RecordingEngineFlowContinuationScheduler()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            flowContinuationScheduler = scheduler,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )
        val run = repository.createRun(
            automationId = saved.id,
            eventType = AutomationEvents.Manual,
            values = emptyMap(),
            status = AutomationRunStatus.Waiting,
            message = "waiting"
        )

        val result = engine.resumeRun(run.id)

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Automation run is waiting without a resumable step", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(run.id)?.status)
        assertTrue(run.id in scheduler.cancelled)
    }

    @Test
    fun resumeRunTerminalizesWaitingRunWhenAutomationIsDisabled() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val scheduler = RecordingEngineFlowContinuationScheduler()
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = executor,
            flowContinuationScheduler = scheduler,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        val runId = waiting.runId ?: error("runId missing")
        repository.setEnabled(saved.id, false)

        val result = engine.resumeRun(runId)

        assertEquals(AutomationRunStatus.Skipped, result.status)
        assertEquals("Automation is disabled", result.message)
        assertEquals(AutomationRunStatus.Skipped, repository.getRun(runId)?.status)
        assertTrue(runId in scheduler.cancelled)
        assertEquals(0, executor.events.size)
    }

    @Test
    fun resumeRunTerminalizesWaitingRunWhenAutomationIsMissing() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val scheduler = RecordingEngineFlowContinuationScheduler()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            flowContinuationScheduler = scheduler,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        val runId = waiting.runId ?: error("runId missing")
        repository.delete(saved.id)

        val result = engine.resumeRun(runId)

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Automation not found", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(runId)?.status)
        assertTrue(runId in scheduler.cancelled)
    }

    @Test
    fun resumeRunTerminalizesWaitingRunWhenConditionsNoLongerPass() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val scheduler = RecordingEngineFlowContinuationScheduler()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            flowContinuationScheduler = scheduler,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                conditions = listOf(
                    AutomationCondition(
                        key = "ready",
                        operator = AutomationOperators.Equals,
                        value = "true"
                    )
                ),
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )
        val waitingStep = saved.flow?.steps?.first() ?: error("waiting step missing")
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
            step = waitingStep,
            stepIndex = 0,
            status = AutomationRunStatus.Waiting,
            attempt = 1,
            message = "waiting"
        )

        val result = engine.resumeRun(run.id)

        assertEquals(AutomationRunStatus.Skipped, result.status)
        assertEquals("Conditions did not pass", result.message)
        assertEquals(AutomationRunStatus.Skipped, repository.getRun(run.id)?.status)
        assertTrue(run.id in scheduler.cancelled)
    }

    @Test
    fun waitStepSchedulesContinuationBeforeResume() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val scheduler = RecordingEngineFlowContinuationScheduler()
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = executor,
            flowContinuationScheduler = scheduler,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "wait", type = AutomationFlowStepTypes.Wait, waitMillis = 5_000L),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Ready")
                        )
                    )
                )
            )
        )

        val waiting = engine.runNow(saved.id)
        val resumed = engine.resumeRun(waiting.runId ?: error("runId missing"))

        assertEquals(AutomationRunStatus.Waiting, waiting.status)
        assertEquals(mapOf(waiting.runId to 5_000L), scheduler.scheduled)
        assertEquals(AutomationRunStatus.Success, resumed.status)
        assertTrue(waiting.runId in scheduler.cancelled)
    }

    @Test
    fun flowAutomationRetriesFailedAction() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = FlakyActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "flaky",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Try"),
                            retryPolicy = AutomationRetryPolicy(maxAttempts = 2)
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id)

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(2, result.stepResults.first().attempts)
        assertEquals(2, executor.calls)
    }

    @Test
    fun flowAutomationReportsNonBlockingSkippedSteps() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "optional-condition",
                            type = AutomationFlowStepTypes.Condition,
                            condition = AutomationCondition(
                                key = "ready",
                                operator = AutomationOperators.Equals,
                                value = "true"
                            ),
                            continueOnFailure = true
                        ),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Still running")
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id, mapOf("ready" to "false"))

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals("Automation ran 2 step(s) with 1 non-blocking skipped step(s)", result.message)
        assertEquals(listOf(AutomationRunStatus.Skipped, AutomationRunStatus.Success), result.stepResults.map { it.status })
        assertEquals(1, executor.events.size)
    }

    @Test
    fun flowAutomationReportsNonBlockingFailedSteps() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = StepAwareActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "optional-action",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Optional"),
                            continueOnFailure = true
                        ),
                        AutomationFlowStep(
                            id = "required-action",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.DraftMessage, messageTemplate = "Required")
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id)

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals("Automation ran 2 step(s) with 1 non-blocking failed step(s)", result.message)
        assertEquals(listOf(AutomationRunStatus.Failed, AutomationRunStatus.Success), result.stepResults.map { it.status })
        assertEquals(listOf(AutomationActionTypes.Notify, AutomationActionTypes.DraftMessage), executor.actionTypes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun flowAutomationAppliesRetryBackoffBetweenFailedAttempts() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = FlakyActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "flaky",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Try"),
                            retryPolicy = AutomationRetryPolicy(maxAttempts = 2, backoffMillis = 1_500L)
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id)

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(2, result.stepResults.first().attempts)
        assertEquals(1_500L, testScheduler.currentTime)
    }

    private fun leaveWorkSpec() = AutomationSpec(
        id = "leave-work",
        name = "Leave work ETA",
        trigger = AutomationTrigger(
            type = AutomationTriggerTypes.Geofence,
            geofence = GeofenceTrigger(
                placeName = "Work",
                latitude = 12.9716,
                longitude = 77.5946,
                radiusMeters = 150f,
                transition = AutomationTriggerTypes.GeofenceExit
            )
        ),
        actions = listOf(
            AutomationAction(
                type = AutomationActionTypes.EtaMessage,
                title = "Send ETA",
                messageTemplate = "I left {{placeName}}. ETA {{etaMinutes}} minutes.",
                recipientName = "Wife",
                requireConfirmation = true,
                metadata = mapOf(
                    "destinationLatitude" to "12.9352",
                    "destinationLongitude" to "77.6245",
                    "averageSpeedKph" to "28"
                )
            )
        )
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

    private fun manualSpec() = AutomationSpec(
        id = "manual-check",
        name = "Manual check",
        trigger = AutomationTrigger(type = AutomationTriggerTypes.Manual),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Check in"))
    )
}

private class RecordingActionExecutor : AutomationActionExecutor {
    val events = mutableListOf<AutomationEvent>()

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        events += event
        return AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
    }
}

private class FlakyActionExecutor : AutomationActionExecutor {
    var calls = 0

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        calls += 1
        return if (calls == 1) {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "try again")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
        }
    }
}

private class StepAwareActionExecutor : AutomationActionExecutor {
    val actionTypes = mutableListOf<String>()

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        actionTypes += action.type
        return if (actionTypes.size == 1) {
            AutomationActionResult(action.type, AutomationRunStatus.Failed, "optional failed")
        } else {
            AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
        }
    }
}

private class FixedEtaProvider : EtaProvider {
    override suspend fun estimate(request: EtaRequest): EtaEstimate =
        EtaEstimate(minutes = 17, distanceKm = 8.2, provider = "fake_routes", confidence = "routed")
}

private class RecordingEngineFlowContinuationScheduler : AutomationFlowContinuationScheduler {
    val scheduled = linkedMapOf<String?, Long>()
    val cancelled = linkedSetOf<String?>()

    override fun schedule(runId: String, delayMillis: Long) {
        scheduled[runId] = delayMillis
    }

    override fun cancel(runId: String) {
        cancelled += runId
    }
}

private class FakeAutomationDao : AutomationDao {
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
