package com.aura.app.automations

interface AutomationActionExecutor {
    suspend fun execute(action: AutomationAction, event: AutomationEvent): AutomationActionResult
}
