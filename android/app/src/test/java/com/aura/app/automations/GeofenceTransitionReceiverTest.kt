package com.aura.app.automations

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTransitionReceiverTest {
    @Test
    fun coordinatorProcessesRemainingGeofencesAndAggregatesFailures() = runTest {
        val processed = mutableListOf<String>()

        val failure = runCatching {
            GeofenceTransitionCoordinator.handle(listOf("first", "second", "third")) { automationId ->
                processed += automationId
                if (automationId != "second") error("$automationId failed")
            }
        }.exceptionOrNull()

        assertEquals(listOf("first", "second", "third"), processed)
        assertEquals("first failed", failure?.message)
        assertEquals(listOf("third failed"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun coordinatorStopsImmediatelyOnCancellation() = runTest {
        val processed = mutableListOf<String>()

        val failure = runCatching {
            GeofenceTransitionCoordinator.handle(listOf("first", "second")) { automationId ->
                processed += automationId
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(listOf("first"), processed)
    }
}
