package com.aura.app.automations

class AutomationConditionEvaluator {
    fun passes(conditions: List<AutomationCondition>, event: AutomationEvent): Boolean =
        conditions.all { condition ->
            val actual = event.values[condition.key]
            when (condition.operator) {
                AutomationOperators.Exists -> !actual.isNullOrBlank()
                AutomationOperators.Equals -> actual == condition.value
                AutomationOperators.NotEquals -> actual != condition.value
                AutomationOperators.Contains -> actual?.contains(condition.value.orEmpty(), ignoreCase = true) == true
                else -> false
            }
        }
}
