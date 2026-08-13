package com.aura.app.widgets

enum class AuraWidgetKind(val wireValue: String) {
    Message("message"),
    Confirmation("confirmation"),
    Itinerary("itinerary"),
    FoodOrder("food_order"),
    Reminder("reminder"),
    Progress("progress"),
    Report("report"),
    MeetingNotes("meeting_notes");

    companion object {
        fun fromWireValue(value: String): AuraWidgetKind? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

enum class AuraWidgetPresentation(val wireValue: String) {
    Compact("compact"),
    Expanded("expanded"),
    Fullscreen("fullscreen");

    companion object {
        fun fromWireValue(value: String): AuraWidgetPresentation? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

enum class AuraWidgetContentFormat(val wireValue: String) {
    PlainText("plain_text"),
    Html("html");

    companion object {
        fun fromWireValue(value: String): AuraWidgetContentFormat? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

enum class AuraWidgetStatus(val wireValue: String) {
    Visible("visible"),
    AwaitingConfirmation("awaiting_confirmation"),
    Executing("executing"),
    Succeeded("succeeded"),
    Failed("failed"),
    Dismissed("dismissed"),
    Expired("expired");

    companion object {
        fun fromWireValue(value: String): AuraWidgetStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class AuraWidgetRisk(val wireValue: String) {
    Low("low"),
    Medium("medium"),
    High("high");

    companion object {
        fun fromWireValue(value: String): AuraWidgetRisk? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

enum class AuraWidgetActionType(val wireValue: String) {
    AssistantMessage("assistant_message"),
    OpenApp("open_app"),
    Dismiss("dismiss");

    companion object {
        fun fromWireValue(value: String): AuraWidgetActionType? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }
    }
}

data class AuraWidgetAction(
    val id: String,
    val label: String,
    val type: AuraWidgetActionType,
    val payload: Map<String, String> = emptyMap(),
    val requiresConfirmation: Boolean = false
)

data class AuraWidgetProposal(
    val kind: AuraWidgetKind,
    val title: String,
    val message: String,
    val details: List<String> = emptyList(),
    val actions: List<AuraWidgetAction> = emptyList(),
    val presentation: AuraWidgetPresentation = AuraWidgetPresentation.Compact,
    val contentFormat: AuraWidgetContentFormat = AuraWidgetContentFormat.PlainText,
    val content: String? = null,
    val risk: AuraWidgetRisk = AuraWidgetRisk.Low,
    val priority: Int = 0,
    val expiresInMinutes: Int = 60,
    val dedupeKey: String = "",
    val source: String = "assistant",
    val assistantRunId: String? = null
)

data class AuraWidget(
    val id: String,
    val kind: AuraWidgetKind,
    val title: String,
    val message: String,
    val details: List<String>,
    val actions: List<AuraWidgetAction>,
    val presentation: AuraWidgetPresentation = AuraWidgetPresentation.Compact,
    val contentFormat: AuraWidgetContentFormat = AuraWidgetContentFormat.PlainText,
    val content: String? = null,
    val status: AuraWidgetStatus,
    val risk: AuraWidgetRisk,
    val priority: Int,
    val source: String,
    val dedupeKey: String,
    val pendingActionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    val lastError: String?,
    val assistantRunId: String? = null
)

data class HostedAndroidWidget(
    val appWidgetId: Int,
    val providerPackage: String,
    val providerClass: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val createdAt: Long,
    val updatedAt: Long
)

sealed interface AuraWidgetActionDecision {
    data class NeedsConfirmation(val widget: AuraWidget, val action: AuraWidgetAction) : AuraWidgetActionDecision
    data class Execute(val widget: AuraWidget, val action: AuraWidgetAction) : AuraWidgetActionDecision
    data object Ignored : AuraWidgetActionDecision
}
