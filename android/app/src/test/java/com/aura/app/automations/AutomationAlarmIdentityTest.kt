package com.aura.app.automations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AutomationAlarmIdentityTest {
    @Test
    fun scheduleAlarmActionsSeparateHashCollisions() {
        val firstId = "Aa"
        val secondId = "BB"
        assertEquals(firstId.hashCode(), secondId.hashCode())

        assertNotEquals(
            ScheduleAutomationScheduler.alarmAction(firstId),
            ScheduleAutomationScheduler.alarmAction(secondId)
        )
    }

    @Test
    fun continuationAlarmActionsSeparateHashCollisions() {
        val firstId = "Aa"
        val secondId = "BB"
        assertEquals(firstId.hashCode(), secondId.hashCode())

        assertNotEquals(
            AlarmAutomationFlowContinuationScheduler.alarmAction(firstId),
            AlarmAutomationFlowContinuationScheduler.alarmAction(secondId)
        )
    }

    @Test
    fun scheduleAndContinuationActionsUseSeparateNamespaces() {
        val id = "same-id"

        assertNotEquals(
            ScheduleAutomationScheduler.alarmAction(id),
            AlarmAutomationFlowContinuationScheduler.alarmAction(id)
        )
    }
}
