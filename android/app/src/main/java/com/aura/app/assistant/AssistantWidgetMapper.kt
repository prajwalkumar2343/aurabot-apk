package com.aura.app.assistant

import com.aura.app.widgets.AuraWidgetAction
import com.aura.app.widgets.AuraWidgetActionType
import com.aura.app.widgets.AuraWidgetKind
import com.aura.app.widgets.AuraWidgetProposal
import com.aura.app.widgets.AuraWidgetPresentation
import com.aura.app.widgets.AuraWidgetContentFormat
import com.aura.app.widgets.AuraWidgetRisk

internal fun ChatWidgetProposal.toAuraWidgetProposal(
    assistantRunId: String? = null,
    dedupeKeyOverride: String? = null,
    sourceOverride: String? = null
): AuraWidgetProposal {
    val widgetKind = kind?.let(AuraWidgetKind::fromWireValue)
        ?: throw IllegalArgumentException("Unsupported widget kind")
    val widgetRisk = risk?.let(AuraWidgetRisk::fromWireValue)
        ?: throw IllegalArgumentException("Unsupported widget risk")
    val widgetPresentation = AuraWidgetPresentation.fromWireValue(presentation)
        ?: throw IllegalArgumentException("Unsupported widget presentation")
    val widgetContentFormat = AuraWidgetContentFormat.fromWireValue(content_format)
        ?: throw IllegalArgumentException("Unsupported widget content format")
    val widgetActions = actions.orEmpty().map { action ->
        val actionType = action.type?.let(AuraWidgetActionType::fromWireValue)
            ?: throw IllegalArgumentException("Unsupported widget action")
        AuraWidgetAction(
            id = action.id.orEmpty(),
            label = action.label.orEmpty(),
            type = actionType,
            payload = action.payload.orEmpty(),
            requiresConfirmation = action.requires_confirmation
        )
    }
    return AuraWidgetProposal(
        kind = widgetKind,
        title = title.orEmpty(),
        message = message.orEmpty(),
        details = details.orEmpty(),
        actions = widgetActions,
        presentation = widgetPresentation,
        contentFormat = widgetContentFormat,
        content = content,
        risk = widgetRisk,
        priority = priority,
        expiresInMinutes = expires_in_minutes,
        dedupeKey = dedupeKeyOverride ?: dedupe_key.orEmpty(),
        source = sourceOverride ?: "assistant",
        assistantRunId = assistantRunId
    )
}
