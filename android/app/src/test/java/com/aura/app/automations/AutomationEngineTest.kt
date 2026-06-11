package com.aura.app.automations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}

private class RecordingActionExecutor : AutomationActionExecutor {
    val events = mutableListOf<AutomationEvent>()

    override suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult {
        events += event
        return AutomationActionResult(action.type, AutomationRunStatus.Success, "ok")
    }
}

private class FixedEtaProvider : EtaProvider {
    override suspend fun estimate(request: EtaRequest): EtaEstimate =
        EtaEstimate(minutes = 17, distanceKm = 8.2, provider = "fake_routes", confidence = "routed")
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
}
