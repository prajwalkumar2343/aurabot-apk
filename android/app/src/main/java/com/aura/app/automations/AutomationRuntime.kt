package com.aura.app.automations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutomationRuntime(
    private val repository: AutomationRepository,
    private val geofenceRegistrar: AutomationGeofenceRegistrar,
    private val scheduleScheduler: AutomationScheduleScheduler
) {
    suspend fun restoreTriggers() = withContext(Dispatchers.IO) {
        val automations = repository.list()
        geofenceRegistrar.restore(automations)
        scheduleScheduler.restore(automations)
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
