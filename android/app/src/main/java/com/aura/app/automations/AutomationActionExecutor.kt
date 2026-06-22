package com.aura.app.automations

interface AutomationActionExecutor {
    suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult
}

internal fun AutomationAction.sendsDirectSms(): Boolean =
    type == AutomationActionTypes.DirectSms && !requireConfirmation

internal fun AutomationAction.hasAtMostOnceSideEffect(): Boolean =
    sendsDirectSms() || isHighImpactCrossAppAction()

internal fun AutomationAction.isHighImpactCrossAppAction(): Boolean {
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
