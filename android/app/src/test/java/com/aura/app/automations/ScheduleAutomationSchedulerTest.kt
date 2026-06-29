package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAutomationSchedulerTest {
    @Test
    fun restoreReconcilesEveryRuleAndAggregatesOrdinaryFailures() {
        val cancelled = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        val automations = listOf(
            scheduleSpec("first"),
            scheduleSpec("second"),
            scheduleSpec("third")
        )

        val failure = runCatching {
            ScheduleRegistrationCoordinator.restore(
                automations = automations,
                cancel = { id ->
                    cancelled += id
                    if (id == "first") error("cancel unavailable")
                },
                schedule = { spec ->
                    scheduled += spec.id
                    if (spec.id == "second") error("alarm unavailable")
                }
            )
        }.exceptionOrNull()

        assertEquals(listOf("first", "second", "third"), cancelled)
        assertEquals(listOf("first", "second", "third"), scheduled)
        assertEquals(
            "Schedule restoration failed: Failed to cancel automation alarm 'first'; " +
                "Failed to schedule automation alarm 'second'",
            failure?.message
        )
        assertEquals("cancel unavailable", failure?.cause?.cause?.message)
        assertEquals(listOf("alarm unavailable"), failure?.suppressed?.map { it.cause?.message })
    }

    @Test
    fun restoreCancelsAllKnownRulesButSchedulesOnlyEnabledScheduleRules() {
        val cancelled = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        val automations = listOf(
            scheduleSpec("enabled"),
            scheduleSpec("disabled", enabled = false),
            manualSpec("manual"),
            scheduleSpec("")
        )

        ScheduleRegistrationCoordinator.restore(
            automations = automations,
            cancel = { cancelled += it },
            schedule = { scheduled += it.id }
        )

        assertEquals(listOf("enabled", "disabled", "manual"), cancelled)
        assertEquals(listOf("enabled"), scheduled)
    }

    @Test
    fun restoreDeduplicatesRuleIdentities() {
        val cancelled = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        val spec = scheduleSpec("duplicate")

        ScheduleRegistrationCoordinator.restore(
            automations = listOf(spec, spec),
            cancel = { cancelled += it },
            schedule = { scheduled += it.id }
        )

        assertEquals(listOf("duplicate"), cancelled)
        assertEquals(listOf("duplicate"), scheduled)
    }

    @Test
    fun restoreStopsImmediatelyOnCancellation() {
        val events = mutableListOf<String>()

        val failure = runCatching {
            ScheduleRegistrationCoordinator.restore(
                automations = listOf(scheduleSpec("first"), scheduleSpec("second")),
                cancel = { id ->
                    events += "cancel:$id"
                    throw CancellationException("cancelled")
                },
                schedule = { events += "schedule:${it.id}" }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf("cancel:first"), events)
    }

    @Test
    fun restoreDoesNotInterceptFatalErrors() {
        val failure = runCatching {
            ScheduleRegistrationCoordinator.restore(
                automations = listOf(scheduleSpec("fatal")),
                cancel = { throw AssertionError("fatal") },
                schedule = {}
            )
        }.exceptionOrNull()

        assertTrue(failure is AssertionError)
    }

    private fun scheduleSpec(id: String, enabled: Boolean = true) = AutomationSpec(
        id = id,
        name = id.ifBlank { "blank" },
        enabled = enabled,
        trigger = AutomationTrigger(
            type = AutomationTriggerTypes.Schedule,
            schedule = ScheduleTrigger(mode = "daily", localTime = "09:00")
        ),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Run"))
    )

    private fun manualSpec(id: String) = AutomationSpec(
        id = id,
        name = id,
        trigger = AutomationTrigger(type = AutomationTriggerTypes.Manual),
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, messageTemplate = "Run"))
    )
}
