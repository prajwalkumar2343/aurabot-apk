package com.aura.app.automations

class AutomationConditionEvaluator {
    fun passes(conditions: List<AutomationCondition>, event: AutomationEvent): Boolean =
        conditions.all { condition ->
            val actual = event.values[condition.key]
            when (condition.operator) {
                AutomationOperators.Exists -> !actual.isNullOrBlank()
                AutomationOperators.Equals -> actual != null && condition.value != null && actual == condition.value
                AutomationOperators.NotEquals -> actual != null && condition.value != null && actual != condition.value
                AutomationOperators.Contains -> actual != null &&
                    !condition.value.isNullOrEmpty() &&
                    actual.contains(condition.value, ignoreCase = true)
                else -> false
            }
        }
}
