package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun topLevelConditionsUseEnrichedEtaContext() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            contextEnricher = DefaultAutomationContextEnricher(FixedEtaProvider()),
            actionExecutor = executor,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            leaveWorkSpec().copy(
                conditions = listOf(
                    AutomationCondition(
                        key = "etaProvider",
                        operator = AutomationOperators.Equals,
                        value = "fake_routes"
                    ),
                    AutomationCondition(
                        key = "placeName",
                        operator = AutomationOperators.Equals,
                        value = "Work"
                    )
                )
            )
        )

        val result = engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                values = mapOf("latitude" to "12.9716", "longitude" to "77.5946")
            )
        )

        assertEquals(AutomationRunStatus.Success, result.single().status)
        assertEquals(1, executor.events.size)
        assertEquals("fake_routes", executor.events.single().values["etaProvider"])
    }

    @Test
    fun failedEnrichedConditionDoesNotCreateRun() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            contextEnricher = DefaultAutomationContextEnricher(FixedEtaProvider()),
            actionExecutor = executor,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            leaveWorkSpec().copy(
                conditions = listOf(
                    AutomationCondition(
                        key = "etaProvider",
                        operator = AutomationOperators.Equals,
                        value = "different_provider"
                    )
                )
            )
        )

        val result = engine.handle(
            AutomationEvent(type = AutomationEvents.GeofenceExit, automationId = saved.id)
        )

        assertEquals(AutomationRunStatus.Skipped, result.single().status)
        assertEquals("Conditions did not pass", result.single().message)
        assertTrue(repository.runs(saved.id).isEmpty())
        assertTrue(executor.events.isEmpty())
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
    fun cooldownUsesLocalAcceptanceTimeForStaleEvents() = runTest {
        var now = 100_000L
        val repository = AutomationRepository(FakeAutomationDao(), clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val saved = repository.upsert(leaveWorkSpec().copy(cooldownMillis = 60_000L))

        engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                occurredAt = 1L
            )
        )
        now += 1_000L
        val repeated = engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                occurredAt = 2L
            )
        )

        assertEquals(AutomationRunStatus.Skipped, repeated.single().status)
        assertEquals(1, executor.events.size)
        assertEquals(100_000L, repository.lastTriggeredAt(saved.id))
    }

    @Test
    fun futureEventTimestampDoesNotPoisonCooldown() = runTest {
        var now = 1_000L
        val repository = AutomationRepository(FakeAutomationDao(), clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val saved = repository.upsert(leaveWorkSpec().copy(cooldownMillis = 60_000L))

        engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                occurredAt = Long.MAX_VALUE
            )
        )
        now += 60_001L
        val afterCooldown = engine.handle(
            AutomationEvent(
                type = AutomationEvents.GeofenceExit,
                automationId = saved.id,
                occurredAt = Long.MAX_VALUE
            )
        )

        assertEquals(AutomationRunStatus.Success, afterCooldown.single().status)
        assertEquals(2, executor.events.size)
        assertEquals(now, repository.lastTriggeredAt(saved.id))
    }

    @Test
    fun futurePersistedCooldownTimestampSelfHeals() = runTest {
        val now = 1_000L
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val saved = repository.upsert(leaveWorkSpec().copy(cooldownMillis = 60_000L))
        dao.markTriggered(saved.id, Long.MAX_VALUE, Long.MAX_VALUE)

        val recovered = engine.handle(
            AutomationEvent(type = AutomationEvents.GeofenceExit, automationId = saved.id)
        )
        val repeated = engine.handle(
            AutomationEvent(type = AutomationEvents.GeofenceExit, automationId = saved.id)
        )

        assertEquals(AutomationRunStatus.Success, recovered.single().status)
        assertEquals(AutomationRunStatus.Skipped, repeated.single().status)
        assertEquals(now, repository.lastTriggeredAt(saved.id))
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
    fun confirmedDirectSmsUsesReviewPathWithoutSmsPermission() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val confirmedSms = leaveWorkSpec().copy(
            actions = listOf(
                AutomationAction(
                    type = AutomationActionTypes.DirectSms,
                    messageTemplate = "I left {{placeName}}.",
                    recipientAddress = "+15555550123",
                    requireConfirmation = true
                )
            )
        )

        val saved = repository.upsert(confirmedSms)
        val action = saved.actions.single()
        val permissions = AutomationPermissionPlanner().requiredPermissions(saved)

        assertTrue(action.requireConfirmation)
        assertFalse(action.sendsDirectSms())
        assertFalse(permissions.any { it.endsWith("SEND_SMS") })
    }

    @Test
    fun onlyExplicitUnconfirmedDirectSmsUsesDirectDeliveryPolicy() {
        val confirmed = AutomationAction(
            type = AutomationActionTypes.DirectSms,
            requireConfirmation = true
        )
        val unconfirmed = confirmed.copy(requireConfirmation = false)

        assertFalse(confirmed.sendsDirectSms())
        assertTrue(unconfirmed.sendsDirectSms())
        assertFalse(confirmed.copy(type = AutomationActionTypes.DraftMessage).sendsDirectSms())
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
    fun validatorRejectsRetriesForIrreversibleActions() {
        assertThrows(IllegalArgumentException::class.java) {
            AutomationValidator.validate(
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
                                ),
                                retryPolicy = AutomationRetryPolicy(maxAttempts = 2)
                            )
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
                                id = "sms",
                                type = AutomationFlowStepTypes.Action,
                                action = AutomationAction(
                                    type = AutomationActionTypes.DirectSms,
                                    messageTemplate = "On my way",
                                    recipientAddress = "+15555550123",
                                    requireConfirmation = false
                                ),
                                retryPolicy = AutomationRetryPolicy(maxAttempts = 2)
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun validatorAllowsRetriesForConfirmedSmsReviewNotifications() {
        val validated = AutomationValidator.validate(
            manualSpec().copy(
                actions = emptyList(),
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "review-sms",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(
                                type = AutomationActionTypes.DirectSms,
                                messageTemplate = "On my way",
                                recipientAddress = "+15555550123",
                                requireConfirmation = true
                            ),
                            retryPolicy = AutomationRetryPolicy(maxAttempts = 2)
                        )
                    )
                )
            )
        )

        assertEquals(2, validated.flow?.steps?.single()?.retryPolicy?.maxAttempts)
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
    fun validatorBoundsFlowRetriesAndWaitDurations() {
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
                id = "too-many-attempts",
                type = AutomationFlowStepTypes.Action,
                action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Retry"),
                retryPolicy = AutomationRetryPolicy(maxAttempts = 6)
            )
        )
        invalid(
            AutomationFlowStep(
                id = "too-much-backoff",
                type = AutomationFlowStepTypes.Action,
                action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Retry"),
                retryPolicy = AutomationRetryPolicy(maxAttempts = 2, backoffMillis = 30_001L)
            )
        )
        invalid(
            AutomationFlowStep(
                id = "too-long-wait",
                type = AutomationFlowStepTypes.Wait,
                waitMillis = 604_800_001L
            )
        )

        val valid = AutomationValidator.validate(
            manualSpec().copy(
                actions = emptyList(),
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "bounded-retry",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Retry"),
                            retryPolicy = AutomationRetryPolicy(maxAttempts = 5, backoffMillis = 30_000L)
                        ),
                        AutomationFlowStep(
                            id = "bounded-wait",
                            type = AutomationFlowStepTypes.Wait,
                            waitMillis = 604_800_000L
                        )
                    )
                )
            )
        )

        assertEquals(listOf("bounded-retry", "bounded-wait"), valid.flow?.steps?.map { it.id })
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
    fun resumeRunFailsClosedWhenAutomationChangedAfterCheckpoint() = runTest {
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
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Original")
                        )
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        repository.upsert(
            saved.copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Changed")
                        )
                    )
                )
            )
        )

        val result = engine.resumeRun(waiting.runId ?: error("runId missing"))

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Automation changed while run was waiting", result.message)
        assertEquals(0, executor.events.size)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun resumeRunAllowsUnchangedAutomationSavedAtNewTimestamp() = runTest {
        var now = 1_000L
        val repository = AutomationRepository(FakeAutomationDao(), clock = { now })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { now })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Confirmed")
                        )
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        now = 2_000L
        repository.upsert(saved)

        val result = engine.resumeRun(waiting.runId ?: error("runId missing"))

        assertEquals(AutomationRunStatus.Success, result.status)
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
    fun concurrentManualRunSkipsWhileDefaultPolicyRunIsExecuting() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockedAutomationIds = setOf("manual-check"))
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(manualSpec())

        val first = async { engine.runNow(saved.id) }
        executor.firstStarted.await()
        val overlapping = engine.runNow(saved.id)
        executor.release.complete(Unit)
        val completed = first.await()

        assertEquals(AutomationRunStatus.Success, completed.status)
        assertEquals(AutomationRunStatus.Skipped, overlapping.status)
        assertEquals("Automation already has an active run", overlapping.message)
        assertEquals(1, executor.events.size)
    }

    @Test
    fun automationDisabledDuringEnrichmentDoesNotStart() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val saved = repository.upsert(manualSpec())
        val engine = AutomationEngine(
            repository = repository,
            contextEnricher = object : AutomationContextEnricher {
                override suspend fun enrich(spec: AutomationSpec, event: AutomationEvent): AutomationEvent {
                    repository.setEnabled(spec.id, false)
                    return event.copy(automationId = spec.id)
                }
            },
            actionExecutor = executor,
            clock = { 1_000L }
        )

        val result = engine.runNow(saved.id)

        assertEquals(AutomationRunStatus.Skipped, result.status)
        assertEquals("Automation is disabled", result.message)
        assertEquals(0, executor.events.size)
        assertTrue(repository.runs(saved.id).isEmpty())
    }

    @Test
    fun allowParallelPolicyRunsActionsConcurrently() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockAll = true)
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    concurrencyPolicy = AutomationConcurrencyPolicies.AllowParallel,
                    steps = listOf(
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Run")
                        )
                    )
                )
            )
        )

        val first = async { engine.runNow(saved.id) }
        val second = async { engine.runNow(saved.id) }
        executor.twoStarted.await()
        executor.release.complete(Unit)
        val results = listOf(first.await(), second.await())

        assertEquals(listOf(AutomationRunStatus.Success, AutomationRunStatus.Success), results.map { it.status })
        assertEquals(2, executor.events.size)
        assertEquals(2, repository.runs(saved.id).size)
    }

    @Test
    fun executingAutomationDoesNotBlockDifferentAutomation() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockedAutomationIds = setOf("manual-a"))
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val firstSpec = repository.upsert(manualSpec().copy(id = "manual-a", name = "Manual A"))
        val secondSpec = repository.upsert(manualSpec().copy(id = "manual-b", name = "Manual B"))

        val first = async { engine.runNow(firstSpec.id) }
        executor.firstStarted.await()
        val second = engine.runNow(secondSpec.id)
        executor.release.complete(Unit)

        assertEquals(AutomationRunStatus.Success, second.status)
        assertEquals(AutomationRunStatus.Success, first.await().status)
        assertEquals(listOf("manual-a", "manual-b"), executor.events.map { it.automationId })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun crossAppAutomationsSerializeExecutionAcrossDifferentRules() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockAll = true)
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val firstSpec = repository.upsert(crossAppSpec("cross-app-a", "Cross app A"))
        val secondSpec = repository.upsert(crossAppSpec("cross-app-b", "Cross app B"))

        val first = async { engine.runNow(firstSpec.id) }
        executor.firstStarted.await()
        val second = async { engine.runNow(secondSpec.id) }
        runCurrent()

        assertEquals(1, executor.events.size)
        assertEquals(AutomationRunStatus.Running, repository.activeRun(secondSpec.id)?.status)
        executor.release.complete(Unit)
        val results = listOf(first.await(), second.await())

        assertEquals(listOf(AutomationRunStatus.Success, AutomationRunStatus.Success), results.map { it.status })
        assertEquals(2, executor.events.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellingRunWaitingForCrossAppLockTerminalizesClaimedRun() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockAll = true)
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val firstSpec = repository.upsert(crossAppSpec("cross-app-a", "Cross app A"))
        val secondSpec = repository.upsert(crossAppSpec("cross-app-b", "Cross app B"))

        val first = async { engine.runNow(firstSpec.id) }
        executor.firstStarted.await()
        val second = async { engine.runNow(secondSpec.id) }
        runCurrent()
        val claimedRun = repository.activeRun(secondSpec.id) ?: error("second run was not claimed")

        second.cancel()
        val cancellation = runCatching { second.await() }.exceptionOrNull()
        executor.release.complete(Unit)
        first.await()

        assertTrue(cancellation is CancellationException)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(claimedRun.id)?.status)
        assertEquals("Automation run was cancelled", repository.getRun(claimedRun.id)?.message)
        assertEquals(null, repository.activeRun(secondSpec.id))
        assertEquals(1, executor.events.size)
    }

    @Test
    fun duplicateResumeSkipsWhileFirstResumeIsExecuting() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ControlledConcurrencyExecutor(blockedAutomationIds = setOf("manual-check"))
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "confirm", type = AutomationFlowStepTypes.Checkpoint),
                        AutomationFlowStep(
                            id = "notify",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Confirmed")
                        )
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        val runId = waiting.runId ?: error("runId missing")

        val firstResume = async { engine.resumeRun(runId) }
        executor.firstStarted.await()
        val duplicate = engine.resumeRun(runId)
        executor.release.complete(Unit)

        assertEquals(AutomationRunStatus.Success, firstResume.await().status)
        assertEquals(AutomationRunStatus.Skipped, duplicate.status)
        assertEquals("Automation run is not waiting", duplicate.message)
        assertEquals(1, executor.events.size)
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
    fun failWaitingRunTerminalizesRunAndCancelsContinuation() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val continuations = RecordingEngineFlowContinuationScheduler()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            flowContinuationScheduler = continuations,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "wait", type = AutomationFlowStepTypes.Wait, waitMillis = 5_000L)
                    )
                )
            )
        )
        val waiting = engine.runNow(saved.id)
        val runId = waiting.runId ?: error("runId missing")

        val failed = engine.failWaitingRun(runId, "Continuation delivery exhausted")

        assertEquals(AutomationRunStatus.Failed, failed.status)
        assertEquals("Continuation delivery exhausted", repository.getRun(runId)?.message)
        assertTrue(runId in continuations.cancelled)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun failWaitingRunDoesNotOverwriteCompletedRun() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            clock = { 1_000L }
        )
        val saved = repository.upsert(manualSpec())
        val completed = engine.runNow(saved.id)
        val runId = completed.runId ?: error("runId missing")

        val abandoned = engine.failWaitingRun(runId, "Late continuation failure")

        assertEquals(AutomationRunStatus.Skipped, abandoned.status)
        assertEquals(AutomationRunStatus.Success, repository.getRun(runId)?.status)
        assertEquals(completed.message, repository.getRun(runId)?.message)
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
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
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
        dao.deleteAutomation(saved.id)

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
    fun flowAutomationRetriesThrownActionException() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val executor = ThrowingOnceActionExecutor()
        val engine = AutomationEngine(repository = repository, actionExecutor = executor, clock = { 1_000L })
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(
                            id = "flaky-exception",
                            type = AutomationFlowStepTypes.Action,
                            action = AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Try"),
                            retryPolicy = AutomationRetryPolicy(maxAttempts = 2)
                        )
                    )
                )
            )
        )

        val result = engine.runNow(saved.id)
        val runId = result.runId ?: error("runId missing")

        assertEquals(AutomationRunStatus.Success, result.status)
        assertEquals(2, result.stepResults.first().attempts)
        assertEquals(
            listOf(AutomationRunStatus.Failed, AutomationRunStatus.Success),
            repository.stepRuns(runId).map { it.status }
        )
        assertEquals("Action execution failed: temporary executor failure", repository.stepRuns(runId).first().message)
    }

    @Test
    fun flowAutomationTerminalizesRunWhenActionExceptionExhaustsRetries() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = AlwaysThrowingActionExecutor(),
            clock = { 1_000L }
        )
        val saved = repository.upsert(manualSpec())

        val result = engine.runNow(saved.id)
        val runId = result.runId ?: error("runId missing")

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Action execution failed: executor unavailable", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(runId)?.status)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun flowAutomationTerminalizesRunWhenStepPersistenceFails() = runTest {
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = executor,
            clock = { 1_000L }
        )
        val saved = repository.upsert(manualSpec())
        dao.failNextStepInsert = true

        val result = engine.runNow(saved.id)
        val runId = result.runId ?: error("runId missing")

        assertEquals(1, executor.events.size)
        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Automation execution failed: step storage unavailable", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(runId)?.status)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun resumedFlowTerminalizesClaimedRunWhenStepPersistenceFails() = runTest {
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val executor = RecordingActionExecutor()
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = executor,
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "wait", type = AutomationFlowStepTypes.Wait, waitMillis = 1L),
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
        dao.failNextStepInsert = true

        val result = engine.resumeRun(runId)

        assertEquals(AutomationRunStatus.Waiting, waiting.status)
        assertEquals(1, executor.events.size)
        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Automation execution failed: step storage unavailable", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(runId)?.status)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun flowAutomationPreservesExecutionFailureWhenTerminalizationAlsoFails() = runTest {
        val dao = FakeAutomationDao()
        val repository = AutomationRepository(dao, clock = { 1_000L })
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            clock = { 1_000L }
        )
        val saved = repository.upsert(manualSpec())
        dao.failTerminalizationAfterStepInsertFailure = true

        val failure = runCatching { engine.runNow(saved.id) }.exceptionOrNull()

        assertEquals("step storage unavailable", failure?.message)
        assertEquals(listOf("run storage unavailable"), failure?.suppressed?.map { it.message })
        assertEquals(AutomationRunStatus.Running, repository.activeRun(saved.id)?.status)
    }

    @Test
    fun waitStepTerminalizesRunWhenContinuationSchedulingFails() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = RecordingActionExecutor(),
            flowContinuationScheduler = FailingEngineFlowContinuationScheduler(),
            clock = { 1_000L }
        )
        val saved = repository.upsert(
            manualSpec().copy(
                flow = AutomationFlow(
                    steps = listOf(
                        AutomationFlowStep(id = "wait", type = AutomationFlowStepTypes.Wait, waitMillis = 5_000L)
                    )
                )
            )
        )

        val result = engine.runNow(saved.id)
        val runId = result.runId ?: error("runId missing")

        assertEquals(AutomationRunStatus.Failed, result.status)
        assertEquals("Failed to schedule flow continuation: alarm service unavailable", result.message)
        assertEquals(AutomationRunStatus.Failed, repository.getRun(runId)?.status)
        assertEquals(null, repository.activeRun(saved.id))
    }

    @Test
    fun flowAutomationPropagatesActionCancellation() = runTest {
        val repository = AutomationRepository(FakeAutomationDao(), clock = { 1_000L })
        val engine = AutomationEngine(
            repository = repository,
            actionExecutor = CancellingActionExecutor(),
            clock = { 1_000L }
        )
        val saved = repository.upsert(manualSpec())

        val failure = runCatching { engine.runNow(saved.id) }.exceptionOrNull()
        val run = repository.runs(saved.id).single()

        assertTrue(failure is CancellationException)
        assertEquals(AutomationRunStatus.Failed, run.status)
        assertEquals("Automation run was cancelled", run.message)
        assertEquals(null, repository.activeRun(saved.id))
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

    private fun crossAppSpec(id: String, name: String) = manualSpec().copy(
        id = id,
        name = name,
        actions = emptyList(),
        flow = AutomationFlow(
            steps = listOf(
                AutomationFlowStep(
                    id = "open-app",
                    type = AutomationFlowStepTypes.Action,
                    action = AutomationAction(
                        type = AutomationActionTypes.OpenApp,
                        metadata = mapOf(AutomationActionMetadata.PackageName to "com.example")
                    )
                )
            )
        )
    )
}

private class RecordingActionExecutor : AutomationActionExecutor {
    val events = mutableListOf<AutomationEvent>()

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        events += event
        return AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
    }
}

private class ControlledConcurrencyExecutor(
    private val blockedAutomationIds: Set<String> = emptySet(),
    private val blockAll: Boolean = false
) : AutomationActionExecutor {
    val events = mutableListOf<AutomationEvent>()
    val firstStarted = CompletableDeferred<Unit>()
    val twoStarted = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        events += event
        firstStarted.complete(Unit)
        if (events.size >= 2) twoStarted.complete(Unit)
        if (blockAll || event.automationId in blockedAutomationIds) release.await()
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

private class ThrowingOnceActionExecutor : AutomationActionExecutor {
    var calls = 0

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        calls += 1
        if (calls == 1) error("temporary executor failure")
        return AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
    }
}

private class AlwaysThrowingActionExecutor : AutomationActionExecutor {
    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        error("executor unavailable")
    }
}

private class CancellingActionExecutor : AutomationActionExecutor {
    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        throw CancellationException("cancelled")
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

private class FailingEngineFlowContinuationScheduler : AutomationFlowContinuationScheduler {
    override fun schedule(runId: String, delayMillis: Long) {
        error("alarm service unavailable")
    }

    override fun cancel(runId: String) = Unit
}

private class FakeAutomationDao : AutomationDao {
    private val automations = linkedMapOf<String, AutomationEntity>()
    private val logs = mutableListOf<AutomationRunLogEntity>()
    var failNextStepInsert = false
    var failTerminalizationAfterStepInsertFailure = false
    private var failNextRunUpsert = false

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
        if (failNextRunUpsert) {
            failNextRunUpsert = false
            error("run storage unavailable")
        }
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
        if (failNextStepInsert || failTerminalizationAfterStepInsertFailure) {
            failNextStepInsert = false
            failNextRunUpsert = failTerminalizationAfterStepInsertFailure
            failTerminalizationAfterStepInsertFailure = false
            error("step storage unavailable")
        }
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
