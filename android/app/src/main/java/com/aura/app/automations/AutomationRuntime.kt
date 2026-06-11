package com.aura.app.automations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutomationRuntime(
    private val repository: AutomationRepository,
    private val geofenceRegistrar: GeofenceAutomationRegistrar,
    private val scheduleScheduler: ScheduleAutomationScheduler
) {
    suspend fun restoreTriggers() = withContext(Dispatchers.IO) {
        val enabled = repository.listEnabled()
        geofenceRegistrar.restore(enabled)
        scheduleScheduler.restore(enabled)
    }

    suspend fun upsertAndRestore(spec: AutomationSpec): AutomationSpec = withContext(Dispatchers.IO) {
        val saved = repository.upsert(spec)
        restoreTriggers()
        saved
    }

    suspend fun deleteAndRestore(id: String) = withContext(Dispatchers.IO) {
        repository.delete(id)
        runCatching { geofenceRegistrar.remove(id) }
        scheduleScheduler.cancel(id)
        restoreTriggers()
    }
}
