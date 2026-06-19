package com.aura.app.automations

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationConditionEvaluatorTest {
    private val evaluator = AutomationConditionEvaluator()

    @Test
    fun missingKeysFailEveryComparisonOperator() {
        val event = AutomationEvent(values = emptyMap())

        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.Equals, "ready")), event))
        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.NotEquals, "blocked")), event))
        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.Contains, "read")), event))
    }

    @Test
    fun missingOrEmptyOperandsFailClosedForLegacySpecs() {
        val event = AutomationEvent(values = mapOf("state" to "ready"))

        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.Equals, null)), event))
        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.NotEquals, null)), event))
        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.Contains, null)), event))
        assertFalse(evaluator.passes(listOf(condition(AutomationOperators.Contains, "")), event))
    }

    @Test
    fun comparisonsPassOnlyWithPresentMatchingContext() {
        val event = AutomationEvent(values = mapOf("state" to "Ready for review"))

        assertTrue(evaluator.passes(listOf(condition(AutomationOperators.Equals, "Ready for review")), event))
        assertTrue(evaluator.passes(listOf(condition(AutomationOperators.NotEquals, "blocked")), event))
        assertTrue(evaluator.passes(listOf(condition(AutomationOperators.Contains, "READY")), event))
    }

    @Test
    fun validatorRejectsMissingComparisonOperands() {
        listOf(
            condition(AutomationOperators.Equals, null),
            condition(AutomationOperators.NotEquals, null),
            condition(AutomationOperators.Contains, null),
            condition(AutomationOperators.Contains, "")
        ).forEach { invalidCondition ->
            assertThrows(IllegalArgumentException::class.java) {
                AutomationValidator.validate(validSpec(invalidCondition))
            }
        }
    }

    private fun condition(operator: String, value: String?) = AutomationCondition(
        key = "state",
        operator = operator,
        value = value
    )

    private fun validSpec(condition: AutomationCondition) = AutomationSpec(
        id = "condition-test",
        name = "Condition test",
        trigger = AutomationTrigger(type = AutomationTriggerTypes.Manual),
        conditions = listOf(condition),
        actions = listOf(
            AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Condition passed")
        )
    )
}
