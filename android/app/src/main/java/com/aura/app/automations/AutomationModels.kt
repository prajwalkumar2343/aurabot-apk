package com.aura.app.automations

data class AutomationSpec(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: AutomationTrigger = AutomationTrigger(AutomationTriggerTypes.Manual),
    val conditions: List<AutomationCondition> = emptyList(),
    val actions: List<AutomationAction> = emptyList(),
    val flow: AutomationFlow? = null,
    val cooldownMillis: Long = 0L,
    val createdBy: String = "assistant",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class AutomationTrigger(
    val type: String = AutomationTriggerTypes.Manual,
    val schedule: ScheduleTrigger? = null,
    val geofence: GeofenceTrigger? = null,
    val manual: ManualTrigger? = null
)

data class ScheduleTrigger(
    val mode: String = "daily",
    val localTime: String? = null,
    val intervalMinutes: Int? = null,
    val daysOfWeek: List<Int> = emptyList()
)

data class GeofenceTrigger(
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Float = 150f,
    val transition: String = AutomationTriggerTypes.GeofenceExit
)

data class ManualTrigger(
    val eventName: String = AutomationEvents.Manual
)

data class AutomationCondition(
    val type: String = "event",
    val key: String = "",
    val operator: String = AutomationOperators.Exists,
    val value: String? = null
)

data class AutomationAction(
    val type: String = AutomationActionTypes.Notify,
    val title: String? = null,
    val messageTemplate: String? = null,
    val recipientName: String? = null,
    val recipientAddress: String? = null,
    val requireConfirmation: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

data class AutomationFlow(
    val steps: List<AutomationFlowStep> = emptyList(),
    val concurrencyPolicy: String = AutomationConcurrencyPolicies.SkipIfRunning
)

data class AutomationFlowStep(
    val id: String = "",
    val name: String = "",
    val type: String = AutomationFlowStepTypes.Action,
    val action: AutomationAction? = null,
    val condition: AutomationCondition? = null,
    val waitMillis: Long = 0L,
    val retryPolicy: AutomationRetryPolicy = AutomationRetryPolicy(),
    val continueOnFailure: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

data class AutomationRetryPolicy(
    val maxAttempts: Int = 1,
    val backoffMillis: Long = 0L
)

data class AutomationEvent(
    val type: String = AutomationEvents.Manual,
    val automationId: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    val values: Map<String, String> = emptyMap()
)

data class AutomationRunResult(
    val automationId: String,
    val status: String,
    val message: String,
    val actionResults: List<AutomationActionResult> = emptyList(),
    val runId: String? = null,
    val stepResults: List<AutomationStepResult> = emptyList()
)

data class AutomationActionResult(
    val actionType: String,
    val status: String,
    val message: String
)

data class AutomationStepResult(
    val stepId: String,
    val stepType: String,
    val status: String,
    val message: String,
    val attempts: Int = 1,
    val actionResult: AutomationActionResult? = null
)

data class AutomationRunLog(
    val id: String,
    val automationId: String,
    val eventType: String,
    val status: String,
    val message: String,
    val createdAt: Long
)

data class AutomationRunRecord(
    val id: String,
    val automationId: String,
    val eventType: String,
    val status: String,
    val message: String,
    val values: Map<String, String>,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

data class AutomationStepRunRecord(
    val id: String,
    val runId: String,
    val automationId: String,
    val stepId: String,
    val stepIndex: Int,
    val stepType: String,
    val actionType: String?,
    val status: String,
    val attempt: Int,
    val message: String,
    val startedAt: Long,
    val completedAt: Long? = null
)

object AutomationTriggerTypes {
    const val Geofence = "geofence"
    const val Schedule = "schedule"
    const val Manual = "manual"
    const val GeofenceEnter = "enter"
    const val GeofenceExit = "exit"
}

object AutomationEvents {
    const val Manual = "manual"
    const val GeofenceEnter = "geofence.enter"
    const val GeofenceExit = "geofence.exit"
    const val ScheduleTick = "schedule.tick"
}

object AutomationActionTypes {
    const val Notify = "notify"
    const val DraftMessage = "draft_message"
    const val EtaMessage = "eta_message"
    const val DirectSms = "direct_sms"
    const val OpenApp = "open_app"
    const val TapText = "tap_text"
    const val TapBounds = "tap_bounds"
    const val TypeText = "type_text"
    const val WaitForText = "wait_for_text"
    const val TapTarget = "tap_target"
    const val LongPressTarget = "long_press_target"
    const val ClearText = "clear_text"
    const val Scroll = "scroll"
    const val Swipe = "swipe"
    const val PressBack = "press_back"
    const val PressHome = "press_home"
}

object AutomationActionMetadata {
    const val PackageName = "packageName"
    const val AppQuery = "appQuery"
    const val Text = "text"
    const val TargetText = "targetText"
    const val ContentDescription = "contentDescription"
    const val ViewId = "viewId"
    const val ClassName = "className"
    const val PartialMatch = "partialMatch"
    const val ClickableOnly = "clickableOnly"
    const val EditableOnly = "editableOnly"
    const val EnabledOnly = "enabledOnly"
    const val Occurrence = "occurrence"
    const val TimeoutMillis = "timeoutMillis"
    const val SettleMillis = "settleMillis"
    const val Direction = "direction"
    const val BoundsLeft = "boundsLeft"
    const val BoundsTop = "boundsTop"
    const val BoundsRight = "boundsRight"
    const val BoundsBottom = "boundsBottom"
    const val StartX = "startX"
    const val StartY = "startY"
    const val EndX = "endX"
    const val EndY = "endY"
    const val DurationMillis = "durationMillis"
}

object AutomationFlowStepTypes {
    const val Action = "action"
    const val Condition = "condition"
    const val Wait = "wait"
    const val Checkpoint = "checkpoint"
}

object AutomationConcurrencyPolicies {
    const val SkipIfRunning = "skip_if_running"
    const val AllowParallel = "allow_parallel"
}

object AutomationOperators {
    const val Exists = "exists"
    const val Equals = "equals"
    const val NotEquals = "not_equals"
    const val Contains = "contains"
}

object AutomationRunStatus {
    const val Running = "running"
    const val Waiting = "waiting"
    const val Success = "success"
    const val Skipped = "skipped"
    const val Failed = "failed"
}
