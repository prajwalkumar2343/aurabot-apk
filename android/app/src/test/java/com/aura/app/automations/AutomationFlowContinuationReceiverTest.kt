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
        val abandoned = mutableListOf<Exception>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { error("resume failed") },
                scheduleRetry = { scheduledAttempts += it },
                abandon = { abandoned += it }
            )
        }.exceptionOrNull()

        assertEquals("resume failed", failure?.message)
        assertEquals(listOf(1), scheduledAttempts)
        assertTrue(abandoned.isEmpty())
    }

    @Test
    fun coordinatorDoesNotRetryAfterRetryIsConsumed() = runTest {
        val scheduledAttempts = mutableListOf<Int>()
        val abandoned = mutableListOf<Exception>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 1,
                resume = { error("resume failed again") },
                scheduleRetry = { scheduledAttempts += it },
                abandon = { abandoned += it }
            )
        }.exceptionOrNull()

        assertEquals("resume failed again", failure?.message)
        assertTrue(scheduledAttempts.isEmpty())
        assertEquals(listOf(failure), abandoned)
    }

    @Test
    fun coordinatorAbandonsRunWhenRetryCannotBeScheduled() = runTest {
        val abandoned = mutableListOf<Exception>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { error("resume failed") },
                scheduleRetry = { error("alarm unavailable") },
                abandon = { abandoned += it }
            )
        }.exceptionOrNull()

        assertEquals("resume failed", failure?.message)
        assertEquals(listOf("alarm unavailable"), failure?.suppressed?.map { it.message })
        assertEquals(listOf(failure), abandoned)
    }

    @Test
    fun coordinatorKeepsResumeFailureWhenAbandonmentAlsoFails() = runTest {
        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 1,
                resume = { error("resume failed") },
                scheduleRetry = {},
                abandon = { error("terminalization failed") }
            )
        }.exceptionOrNull()

        assertEquals("resume failed", failure?.message)
        assertEquals(listOf("terminalization failed"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun coordinatorKeepsRetryCancellationPrimary() = runTest {
        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { error("resume failed") },
                scheduleRetry = { throw CancellationException("cancelled while scheduling") },
                abandon = {}
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals("cancelled while scheduling", failure?.message)
        assertEquals(listOf("resume failed"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun coordinatorDoesNotRetryCancellation() = runTest {
        val scheduledAttempts = mutableListOf<Int>()
        val abandoned = mutableListOf<Exception>()

        val failure = runCatching {
            AutomationFlowContinuationCoordinator.handle(
                retryAttempt = 0,
                resume = { throw CancellationException("cancelled") },
                scheduleRetry = { scheduledAttempts += it },
                abandon = { abandoned += it }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(scheduledAttempts.isEmpty())
        assertTrue(abandoned.isEmpty())
    }
}
