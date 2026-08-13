package com.aura.app.automations

interface AutomationActionExecutor {
    suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult
}

internal fun AutomationAction.sendsDirectSms(): Boolean =
    type == AutomationActionTypes.DirectSms && !requireConfirmation

internal fun AutomationAction.hasAtMostOnceSideEffect(): Boolean =
    sendsDirectSms()
