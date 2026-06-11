package com.aura.app.automations

import kotlinx.coroutines.test.runTest
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
}
