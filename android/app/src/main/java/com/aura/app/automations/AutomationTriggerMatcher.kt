package com.aura.app.automations

class AutomationTriggerMatcher {
    fun matches(spec: AutomationSpec, event: AutomationEvent): Boolean =
        when (spec.trigger.type) {
            AutomationTriggerTypes.Manual -> matchesManual(spec, event)
            AutomationTriggerTypes.Geofence -> matchesGeofence(spec, event)
            AutomationTriggerTypes.Schedule -> event.type == AutomationEvents.ScheduleTick &&
                (event.automationId == null || event.automationId == spec.id)
            else -> false
        }

    private fun matchesManual(spec: AutomationSpec, event: AutomationEvent): Boolean {
        val expected = spec.trigger.manual?.eventName ?: AutomationEvents.Manual
        return if (event.automationId != null) {
            event.automationId == spec.id && event.type == expected
        } else {
            event.type == expected
        }
    }

    private fun matchesGeofence(spec: AutomationSpec, event: AutomationEvent): Boolean {
        val geofence = spec.trigger.geofence ?: return false
        val expectedEvent = when (geofence.transition) {
            AutomationTriggerTypes.GeofenceEnter -> AutomationEvents.GeofenceEnter
            else -> AutomationEvents.GeofenceExit
        }
        return event.type == expectedEvent && event.automationId == spec.id
    }
}
