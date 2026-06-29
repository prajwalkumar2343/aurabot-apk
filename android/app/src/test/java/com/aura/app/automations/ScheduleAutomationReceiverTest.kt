package com.aura.app.automations

import kotlinx.coroutines.CancellationException
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
        assertEquals("execution failed", failure?.message)
        assertEquals(listOf("execute", "reschedule"), events)
    }

    @Test
    fun coordinatorSurfacesRescheduleFailureAfterSuccessfulExecution() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            ScheduleAutomationCoordinator.handle(
                execute = { events += "execute" },
                reschedule = {
                    events += "reschedule"
                    error("reschedule failed")
                }
            )
        }.exceptionOrNull()

        assertEquals("reschedule failed", failure?.message)
        assertEquals(listOf("execute", "reschedule"), events)
    }

    @Test
    fun coordinatorKeepsExecutionFailureWhenRescheduleAlsoFails() = runTest {
        val failure = runCatching {
            ScheduleAutomationCoordinator.handle(
                execute = { error("execution failed") },
                reschedule = { error("reschedule failed") }
            )
        }.exceptionOrNull()

        assertEquals("execution failed", failure?.message)
        assertEquals(listOf("reschedule failed"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun coordinatorKeepsCancellationPrimaryWhenRescheduleFails() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            ScheduleAutomationCoordinator.handle(
                execute = {
                    events += "execute"
                    throw CancellationException("cancelled")
                },
                reschedule = {
                    events += "reschedule"
                    error("reschedule failed")
                }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("cancelled", failure?.message)
        assertEquals(listOf("reschedule failed"), failure?.suppressed?.map { it.message })
        assertEquals(listOf("execute", "reschedule"), events)
    }

    @Test
    fun coordinatorDoesNotInterceptFatalExecutionErrors() = runTest {
        var rescheduled = false

        val failure = runCatching {
            ScheduleAutomationCoordinator.handle(
                execute = { throw AssertionError("fatal") },
                reschedule = { rescheduled = true }
            )
        }.exceptionOrNull()

        assertTrue(failure is AssertionError)
        assertTrue(!rescheduled)
    }
}
