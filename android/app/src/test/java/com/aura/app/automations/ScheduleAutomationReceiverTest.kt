package com.aura.app.automations

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAutomationReceiverTest {
    @Test
    fun coordinatorReschedulesAfterExecutionFailure() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            ScheduleAutomationCoordinator.handle(
                execute = {
                    events += "execute"
                    error("execution failed")
                },
                reschedule = { events += "reschedule" }
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf("execute", "reschedule"), events)
    }
}
