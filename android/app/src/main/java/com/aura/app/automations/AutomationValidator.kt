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
        if (normalizedFlow == null) {
            require(spec.actions.none { it.isHighImpactCrossAppAction() }) {
                "High-impact cross-app actions must be modeled as flow steps with a checkpoint before the action"
            }
        }
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
        var hasPriorCheckpoint = false
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
                AutomationFlowStepTypes.Action -> {
                    val action = requireNotNull(step.action) { "Action flow steps need an action" }
                    validateAction(action)
                    require(!action.isHighImpactCrossAppAction() || hasPriorCheckpoint) {
                        "High-impact cross-app action '${action.type}' needs a prior checkpoint step"
                    }
                }
                AutomationFlowStepTypes.Condition -> validateCondition(
                    requireNotNull(step.condition) { "Condition flow steps need a condition" }
                )
                AutomationFlowStepTypes.Wait -> require(step.waitMillis > 0L) {
                    "Wait flow steps need positive waitMillis"
                }
                AutomationFlowStepTypes.Checkpoint -> hasPriorCheckpoint = true
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
                AutomationActionTypes.DirectSms,
                AutomationActionTypes.OpenApp,
                AutomationActionTypes.WaitForApp,
                AutomationActionTypes.TapText,
                AutomationActionTypes.TapBounds,
                AutomationActionTypes.TypeText,
                AutomationActionTypes.WaitForText,
                AutomationActionTypes.WaitForTarget,
                AutomationActionTypes.WaitUntilGone,
                AutomationActionTypes.WaitForIdle,
                AutomationActionTypes.TapTarget,
                AutomationActionTypes.LongPressTarget,
                AutomationActionTypes.ClearText,
                AutomationActionTypes.Scroll,
                AutomationActionTypes.ScrollUntilTarget,
                AutomationActionTypes.Swipe,
                AutomationActionTypes.InspectScreen,
                AutomationActionTypes.PressBack,
                AutomationActionTypes.PressHome
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
        if (action.type == AutomationActionTypes.OpenApp) {
            require(
                action.metadata[AutomationActionMetadata.PackageName]?.isNotBlank() == true ||
                    action.metadata[AutomationActionMetadata.AppQuery]?.isNotBlank() == true
            ) { "Open app actions need packageName or appQuery metadata" }
        }
        if (action.type == AutomationActionTypes.WaitForApp) {
            require(action.metadata[AutomationActionMetadata.PackageName]?.isNotBlank() == true) {
                "Wait for app actions need packageName metadata"
            }
        }
        if (action.type == AutomationActionTypes.TapText || action.type == AutomationActionTypes.WaitForText) {
            require(action.metadata[AutomationActionMetadata.Text]?.isNotBlank() == true) {
                "${action.type} actions need text metadata"
            }
        }
        if (action.type == AutomationActionTypes.WaitForTarget) {
            require(action.hasSelector()) {
                "Wait for target actions need at least one selector metadata field: text, contentDescription, viewId, or className"
            }
        }
        if (action.type == AutomationActionTypes.WaitUntilGone) {
            require(action.hasSelector()) {
                "Wait until gone actions need at least one selector metadata field: text, contentDescription, viewId, or className"
            }
        }
        if (action.type == AutomationActionTypes.WaitForIdle) {
            action.metadata[AutomationActionMetadata.MaxNodes]?.let { value ->
                val maxNodes = value.toIntOrNull()
                require(maxNodes != null && maxNodes in 1..80) { "Wait for idle maxNodes must be between 1 and 80" }
            }
            action.metadata[AutomationActionMetadata.StableSamples]?.let { value ->
                val stableSamples = value.toIntOrNull()
                require(stableSamples != null && stableSamples in 2..6) { "Wait for idle stableSamples must be between 2 and 6" }
            }
        }
        if (action.type == AutomationActionTypes.TapTarget || action.type == AutomationActionTypes.LongPressTarget) {
            require(action.hasSelector()) {
                "${action.type} actions need at least one selector metadata field: text, contentDescription, viewId, or className"
            }
        }
        if (action.type == AutomationActionTypes.TapBounds) {
            require(action.bounds() != null) { "Tap bounds actions need numeric boundsLeft, boundsTop, boundsRight, and boundsBottom metadata" }
        }
        if (action.type == AutomationActionTypes.TypeText) {
            require(action.metadata[AutomationActionMetadata.Text]?.isNotBlank() == true) {
                "Type text actions need text metadata"
            }
        }
        if (action.type == AutomationActionTypes.ClearText) {
            require(action.hasSelector()) {
                "Clear text actions need at least one selector metadata field: text, contentDescription, viewId, or className"
            }
        }
        if (action.type == AutomationActionTypes.Scroll || action.type == AutomationActionTypes.ScrollUntilTarget) {
            val direction = action.metadata[AutomationActionMetadata.Direction]?.lowercase().orEmpty()
            require(direction in setOf("", "up", "down", "left", "right", "forward", "backward")) {
                "Scroll direction must be up, down, left, right, forward, or backward"
            }
        }
        if (action.type == AutomationActionTypes.ScrollUntilTarget) {
            require(action.hasSelector()) {
                "Scroll until target actions need at least one selector metadata field: text, contentDescription, viewId, or className"
            }
            action.metadata[AutomationActionMetadata.MaxScrolls]?.let { value ->
                val maxScrolls = value.toIntOrNull()
                require(maxScrolls != null && maxScrolls in 1..50) { "Scroll until target maxScrolls must be between 1 and 50" }
            }
        }
        if (action.type == AutomationActionTypes.Swipe) {
            require(action.swipePoints() != null) {
                "Swipe actions need numeric startX, startY, endX, and endY metadata"
            }
        }
        if (action.type == AutomationActionTypes.InspectScreen) {
            action.metadata[AutomationActionMetadata.MaxNodes]?.let { value ->
                val maxNodes = value.toIntOrNull()
                require(maxNodes != null && maxNodes in 1..80) { "Inspect screen maxNodes must be between 1 and 80" }
            }
        }
    }

    private fun AutomationAction.hasSelector(): Boolean =
        listOf(
            AutomationActionMetadata.Text,
            AutomationActionMetadata.TargetText,
            AutomationActionMetadata.ContentDescription,
            AutomationActionMetadata.ViewId,
            AutomationActionMetadata.ClassName
        ).any { key -> metadata[key]?.isNotBlank() == true }

    private fun AutomationAction.bounds(): List<Int>? {
        val values = listOf(
            metadata[AutomationActionMetadata.BoundsLeft],
            metadata[AutomationActionMetadata.BoundsTop],
            metadata[AutomationActionMetadata.BoundsRight],
            metadata[AutomationActionMetadata.BoundsBottom]
        ).map { it?.toIntOrNull() }
        return values.takeIf { it.all { value -> value != null } }?.filterNotNull()
    }

    private fun AutomationAction.swipePoints(): List<Int>? {
        val values = listOf(
            metadata[AutomationActionMetadata.StartX],
            metadata[AutomationActionMetadata.StartY],
            metadata[AutomationActionMetadata.EndX],
            metadata[AutomationActionMetadata.EndY]
        ).map { it?.toIntOrNull() }
        return values.takeIf { it.all { value -> value != null } }?.filterNotNull()
    }

    private fun AutomationAction.isHighImpactCrossAppAction(): Boolean {
        if (type !in highImpactGestureActionTypes) return false
        if (metadata[AutomationActionMetadata.RiskLevel]?.equals("high", ignoreCase = true) == true) {
            return true
        }
        val targetText = listOf(
            metadata[AutomationActionMetadata.Text],
            metadata[AutomationActionMetadata.TargetText],
            metadata[AutomationActionMetadata.ContentDescription]
        ).joinToString(" ").lowercase()
        return highImpactTerms.any { term -> Regex("\\b${Regex.escape(term)}\\b").containsMatchIn(targetText) }
    }

    private val highImpactGestureActionTypes = setOf(
        AutomationActionTypes.TapText,
        AutomationActionTypes.TapTarget,
        AutomationActionTypes.TapBounds,
        AutomationActionTypes.LongPressTarget
    )

    private val highImpactTerms = setOf(
        "buy",
        "cancel",
        "confirm",
        "delete",
        "order",
        "pay",
        "post",
        "publish",
        "purchase",
        "remove",
        "send",
        "share",
        "submit",
        "transfer",
        "unsubscribe",
        "withdraw"
    )
}
