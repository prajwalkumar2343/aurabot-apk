package com.aura.app.automations

data class AutomationSpec(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: AutomationTrigger = AutomationTrigger(AutomationTriggerTypes.Manual),
    val conditions: List<AutomationCondition> = emptyList(),
    val actions: List<AutomationAction> = emptyList(),
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
    val actionResults: List<AutomationActionResult> = emptyList()
)

data class AutomationActionResult(
    val actionType: String,
    val status: String,
    val message: String
)

data class AutomationRunLog(
    val id: String,
    val automationId: String,
    val eventType: String,
    val status: String,
    val message: String,
    val createdAt: Long
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
}

object AutomationOperators {
    const val Exists = "exists"
    const val Equals = "equals"
    const val NotEquals = "not_equals"
    const val Contains = "contains"
}

object AutomationRunStatus {
    const val Success = "success"
    const val Skipped = "skipped"
    const val Failed = "failed"
}
