package com.aura.app.automations

object AutomationValidator {
    fun validate(spec: AutomationSpec): AutomationSpec {
        require(spec.name.isNotBlank()) { "Automation name is required" }
        require(spec.actions.isNotEmpty()) { "Automation needs at least one action" }
        validateTrigger(spec.trigger)
        spec.conditions.forEach { validateCondition(it) }
        spec.actions.forEach { validateAction(it) }
        require(spec.cooldownMillis >= 0L) { "Cooldown cannot be negative" }
        return spec.copy(
            name = spec.name.trim(),
            description = spec.description.trim(),
            actions = spec.actions.map { action ->
                if (action.type == AutomationActionTypes.EtaMessage || action.type == AutomationActionTypes.DraftMessage) {
                    action.copy(requireConfirmation = true)
                } else {
                    action
                }
            }
        )
    }

    private fun validateTrigger(trigger: AutomationTrigger) {
        when (trigger.type) {
            AutomationTriggerTypes.Geofence -> {
                val geofence = requireNotNull(trigger.geofence) { "Geofence trigger config is required" }
                require(geofence.placeName.isNotBlank()) { "Geofence place name is required" }
                require(geofence.latitude in -90.0..90.0) { "Geofence latitude is invalid" }
                require(geofence.longitude in -180.0..180.0) { "Geofence longitude is invalid" }
                require(geofence.radiusMeters in 50f..10_000f) { "Geofence radius must be between 50m and 10km" }
                require(geofence.transition in setOf(AutomationTriggerTypes.GeofenceEnter, AutomationTriggerTypes.GeofenceExit)) {
                    "Geofence transition must be enter or exit"
                }
            }
            AutomationTriggerTypes.Schedule -> {
                val schedule = requireNotNull(trigger.schedule) { "Schedule trigger config is required" }
                require(schedule.mode in setOf("daily", "interval")) { "Schedule mode must be daily or interval" }
                if (schedule.mode == "daily") {
                    require(schedule.localTime?.matches(Regex("\\d{2}:\\d{2}")) == true) {
                        "Daily schedule needs HH:mm localTime"
                    }
                }
                if (schedule.mode == "interval") {
                    require((schedule.intervalMinutes ?: 0) > 0) { "Interval schedule needs positive intervalMinutes" }
                }
            }
            AutomationTriggerTypes.Manual -> Unit
            else -> error("Unsupported automation trigger: ${trigger.type}")
        }
    }

    private fun validateCondition(condition: AutomationCondition) {
        require(condition.key.isNotBlank()) { "Condition key is required" }
        require(
            condition.operator in setOf(
                AutomationOperators.Exists,
                AutomationOperators.Equals,
                AutomationOperators.NotEquals,
                AutomationOperators.Contains
            )
        ) { "Unsupported condition operator: ${condition.operator}" }
    }

    private fun validateAction(action: AutomationAction) {
        require(
            action.type in setOf(
                AutomationActionTypes.Notify,
                AutomationActionTypes.DraftMessage,
                AutomationActionTypes.EtaMessage,
                AutomationActionTypes.DirectSms
            )
        ) {
            "Unsupported automation action: ${action.type}"
        }
        if (
            action.type == AutomationActionTypes.DraftMessage ||
            action.type == AutomationActionTypes.EtaMessage ||
            action.type == AutomationActionTypes.DirectSms
        ) {
            require(action.messageTemplate?.isNotBlank() == true) { "Message actions need a messageTemplate" }
        }
        if (action.type == AutomationActionTypes.DirectSms) {
            require(action.recipientAddress?.isNotBlank() == true) { "Direct SMS actions need a recipientAddress" }
        }
    }
}
