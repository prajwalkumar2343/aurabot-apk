package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationBroadcastWorkTest {
    @Test
    fun recoverableFailureIsReportedWithoutEscapingReceiverWork() = runTest {
        val failures = mutableListOf<Exception>()

        AutomationBroadcastWork.run(
            operation = { error("delivery failed") },
            reportFailure = { failures += it }
        )

        assertEquals(listOf("delivery failed"), failures.map { it.message })
    }

    @Test
    fun reportingFailureDoesNotEscapeReceiverWork() = runTest {
        AutomationBroadcastWork.run(
            operation = { error("delivery failed") },
            reportFailure = { error("logging failed") }
        )
    }

    @Test
    fun cancellationStillPropagates() = runTest {
        val failures = mutableListOf<Exception>()

        val failure = runCatching {
            AutomationBroadcastWork.run(
                operation = { throw CancellationException("cancelled") },
                reportFailure = { failures += it }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun fatalErrorsStillPropagate() = runTest {
        val failure = runCatching {
            AutomationBroadcastWork.run(
                operation = { throw AssertionError("fatal") },
                reportFailure = {}
            )
        }.exceptionOrNull()

        assertTrue(failure is AssertionError)
    }
}
