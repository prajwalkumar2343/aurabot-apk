package com.aura.app.automations

import java.time.LocalTime

object AutomationValidator {
    fun validate(spec: AutomationSpec): AutomationSpec {
        require(spec.name.isNotBlank()) { "Automation name is required" }
        val normalizedFlow = normalizeFlow(spec.flow)
        require(spec.actions.isNotEmpty() || normalizedFlow?.steps?.isNotEmpty() == true) {
            "Automation needs at least one action or flow step"
        }
        validateTrigger(spec.trigger)
        spec.conditions.forEach { validateCondition(it) }
        spec.actions.forEach { validateAction(it) }
        normalizedFlow?.let { validateFlow(it) }
        require(spec.cooldownMillis >= 0L) { "Cooldown cannot be negative" }
        return spec.copy(
            name = spec.name.trim(),
            description = spec.description.trim(),
            actions = spec.actions.map { normalizeAction(it) },
            flow = normalizedFlow
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
                    val localTime = schedule.localTime?.trim().orEmpty()
                    require(localTime.matches(Regex("\\d{2}:\\d{2}")) && runCatching { LocalTime.parse(localTime) }.isSuccess) {
                        "Daily schedule needs a valid HH:mm localTime"
                    }
                    require(schedule.daysOfWeek.all { it in 1..7 }) { "Schedule daysOfWeek values must be 1 through 7" }
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

    private fun validateFlow(flow: AutomationFlow) {
        require(flow.concurrencyPolicy in setOf(AutomationConcurrencyPolicies.SkipIfRunning, AutomationConcurrencyPolicies.AllowParallel)) {
            "Unsupported flow concurrency policy: ${flow.concurrencyPolicy}"
        }
        val ids = mutableSetOf<String>()
        flow.steps.forEach { step ->
            require(step.id.isNotBlank()) { "Flow step id is required" }
            require(ids.add(step.id)) { "Flow step ids must be unique" }
            require(
                step.type in setOf(
                    AutomationFlowStepTypes.Action,
                    AutomationFlowStepTypes.Condition,
                    AutomationFlowStepTypes.Wait,
                    AutomationFlowStepTypes.Checkpoint
                )
            ) { "Unsupported flow step type: ${step.type}" }
            require(step.retryPolicy.maxAttempts >= 1) { "Flow step maxAttempts must be at least 1" }
            require(step.retryPolicy.backoffMillis >= 0L) { "Flow step backoff cannot be negative" }
            require(step.waitMillis >= 0L) { "Flow step waitMillis cannot be negative" }
            when (step.type) {
                AutomationFlowStepTypes.Action -> validateAction(
                    requireNotNull(step.action) { "Action flow steps need an action" }
                )
                AutomationFlowStepTypes.Condition -> validateCondition(
                    requireNotNull(step.condition) { "Condition flow steps need a condition" }
                )
                AutomationFlowStepTypes.Wait -> require(step.waitMillis > 0L) {
                    "Wait flow steps need positive waitMillis"
                }
                AutomationFlowStepTypes.Checkpoint -> Unit
            }
        }
    }

    private fun normalizeFlow(flow: AutomationFlow?): AutomationFlow? {
        val steps = flow?.steps.orEmpty()
        if (steps.isEmpty()) return null
        return flow?.copy(
            steps = steps.mapIndexed { index, step ->
                step.copy(
                    id = step.id.ifBlank { "step-${index + 1}" }.trim(),
                    name = step.name.trim(),
                    action = step.action?.let { normalizeAction(it) }
                )
            }
        )
    }

    private fun normalizeAction(action: AutomationAction): AutomationAction =
        if (action.type == AutomationActionTypes.EtaMessage || action.type == AutomationActionTypes.DraftMessage) {
            action.copy(requireConfirmation = true)
        } else {
            action
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
