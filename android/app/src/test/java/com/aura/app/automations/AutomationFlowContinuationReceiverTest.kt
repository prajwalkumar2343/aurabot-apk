package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationFlowContinuationReceiverTest {
    @Test
    fun coordinatorSchedulesOneRetryAndRethrowsFailure() = runTest {
        val scheduledAttempts = mutableListOf<Int>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { error("resume failed") },
                scheduleRetry = { scheduledAttempts += it }
            )
        }.exceptionOrNull()

        assertEquals("resume failed", failure?.message)
        assertEquals(listOf(1), scheduledAttempts)
    }

    @Test
    fun coordinatorDoesNotRetryAfterRetryIsConsumed() = runTest {
        val scheduledAttempts = mutableListOf<Int>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 1,
                resume = { error("resume failed again") },
                scheduleRetry = { scheduledAttempts += it }
            )
        }.exceptionOrNull()

        assertEquals("resume failed again", failure?.message)
        assertTrue(scheduledAttempts.isEmpty())
    }

    @Test
    fun coordinatorDoesNotRetryCancellation() = runTest {
        val scheduledAttempts = mutableListOf<Int>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { throw CancellationException("cancelled") },
                scheduleRetry = { scheduledAttempts += it }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(scheduledAttempts.isEmpty())
    }
}
