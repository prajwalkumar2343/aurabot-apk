package com.aura.app.automations

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutomationEngine(
    private val repository: AutomationRepository,
    private val triggerMatcher: AutomationTriggerMatcher = AutomationTriggerMatcher(),
    private val conditionEvaluator: AutomationConditionEvaluator = AutomationConditionEvaluator(),
    private val contextEnricher: AutomationContextEnricher = DefaultAutomationContextEnricher(),
    private val actionExecutor: AutomationActionExecutor,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()

    suspend fun handle(event: AutomationEvent): List<AutomationRunResult> = mutex.withLock {
        repository.listEnabled()
            .filter { triggerMatcher.matches(it, event) }
            .map { runAutomation(it, event) }
    }

    suspend fun runNow(automationId: String, values: Map<String, String> = emptyMap()): AutomationRunResult {
        val spec = repository.get(automationId)
            ?: return AutomationRunResult(automationId, AutomationRunStatus.Failed, "Automation not found")
        return runAutomation(
            spec = spec,
            event = AutomationEvent(
                type = AutomationEvents.Manual,
                automationId = automationId,
                values = values
            )
        )
    }

    private suspend fun runAutomation(spec: AutomationSpec, event: AutomationEvent): AutomationRunResult {
        if (!spec.enabled) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is disabled")
        }
        val lastTriggeredAt = repository.lastTriggeredAt(spec.id)
        if (lastTriggeredAt != null && spec.cooldownMillis > 0L && clock() - lastTriggeredAt < spec.cooldownMillis) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Automation is cooling down")
        }
        if (!conditionEvaluator.passes(spec.conditions, event)) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "Conditions did not pass")
        }
        if (spec.actions.isEmpty()) {
            return result(spec.id, event.type, AutomationRunStatus.Skipped, "No actions configured")
        }

        val enrichedEvent = contextEnricher.enrich(spec, event)
        val actionResults = spec.actions.map { action -> actionExecutor.execute(action, enrichedEvent) }
        val failed = actionResults.firstOrNull { it.status == AutomationRunStatus.Failed }
        val status = failed?.status ?: AutomationRunStatus.Success
        val message = failed?.message ?: "Automation ran ${actionResults.size} action(s)"
        repository.markTriggered(spec.id, event.occurredAt)
        repository.log(spec.id, event.type, status, message)
        return AutomationRunResult(spec.id, status, message, actionResults)
    }

    private suspend fun result(
        automationId: String,
        eventType: String,
        status: String,
        message: String
    ): AutomationRunResult {
        repository.log(automationId, eventType, status, message)
        return AutomationRunResult(automationId, status, message)
    }
}
