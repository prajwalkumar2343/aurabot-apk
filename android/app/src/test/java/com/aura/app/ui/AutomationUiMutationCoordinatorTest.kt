package com.aura.app.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationUiMutationCoordinatorTest {
    @Test
    fun refreshesStateAfterOperationFailure() = runTest {
        val events = mutableListOf<String>()

        val failure = AutomationUiMutationCoordinator.run(
            operation = {
                events += "operation"
                error("cleanup failed")
            },
            refresh = { events += "refresh" }
        )

        assertEquals("cleanup failed", failure?.message)
        assertEquals(listOf("operation", "refresh"), events)
    }

    @Test
    fun keepsOperationFailurePrimaryWhenRefreshAlsoFails() = runTest {
        val failure = AutomationUiMutationCoordinator.run(
            operation = { error("operation failed") },
            refresh = { error("refresh failed") }
        )

        assertEquals("operation failed", failure?.message)
        assertEquals(listOf("refresh failed"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun reportsRefreshFailureAfterSuccessfulOperation() = runTest {
        val failure = AutomationUiMutationCoordinator.run(
            operation = {},
            refresh = { error("refresh failed") }
        )

        assertEquals("refresh failed", failure?.message)
    }

    @Test
    fun propagatesOperationCancellationWithoutRefreshing() = runTest {
        var refreshed = false

        val failure = runCatching {
            AutomationUiMutationCoordinator.run(
                operation = { throw CancellationException("cancelled") },
                refresh = { refreshed = true }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(!refreshed)
    }

    @Test
    fun propagatesRefreshCancellation() = runTest {
        val failure = runCatching {
            AutomationUiMutationCoordinator.run(
                operation = {},
                refresh = { throw CancellationException("cancelled") }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }
}
