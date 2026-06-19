package com.aura.app.automations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationExecutionRegistryTest {
    @Test
    fun mutationCancelsAndJoinsTrackedExecutionBeforeApplyingChange() = runTest {
        val registry = AutomationExecutionRegistry()
        val generation = registry.generation("automation")
        val started = CompletableDeferred<Unit>()
        val cleanedUp = CompletableDeferred<Unit>()
        val execution = async {
            registry.track("automation", "run", generation) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cleanedUp.complete(Unit)
                }
            }
        }
        started.await()
        var cleanupFinishedBeforeMutation = false

        registry.mutate(
            "automation",
            AutomationRunStatus.Skipped,
            "disabled"
        ) {
            cleanupFinishedBeforeMutation = cleanedUp.isCompleted
        }
        val failure = runCatching { execution.await() }.exceptionOrNull()

        assertTrue(cleanupFinishedBeforeMutation)
        assertTrue(failure is AutomationConfigurationChangedException)
        assertEquals(AutomationRunStatus.Skipped, (failure as AutomationConfigurationChangedException).terminalStatus)
        assertEquals("disabled", failure.message)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun generationWaitsUntilMutationFinishes() = runTest {
        val registry = AutomationExecutionRegistry()
        val mutationStarted = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        val mutation = async {
            registry.mutate("automation", AutomationRunStatus.Failed, "changed") {
                mutationStarted.complete(Unit)
                releaseMutation.await()
            }
        }
        mutationStarted.await()

        val generation = async { registry.generation("automation") }
        runCurrent()

        assertFalse(generation.isCompleted)
        releaseMutation.complete(Unit)
        mutation.await()
        assertEquals(1L, generation.await())
    }

    @Test
    fun staleGenerationCannotStartExecutionAfterMutation() = runTest {
        val registry = AutomationExecutionRegistry()
        val staleGeneration = registry.generation("automation")
        registry.mutate("automation", AutomationRunStatus.Failed, "changed") {}
        var executed = false

        val failure = runCatching {
            registry.track("automation", "run", staleGeneration) {
                executed = true
            }
        }.exceptionOrNull()

        assertTrue(failure is AutomationConfigurationChangedException)
        assertEquals("Automation configuration changed before execution", failure?.message)
        assertFalse(executed)
    }

    @Test
    fun executionCannotMutateItsOwnAutomation() = runTest {
        val registry = AutomationExecutionRegistry()
        val generation = registry.generation("automation")

        val failure = registry.track("automation", "run", generation) {
            runCatching {
                registry.mutate("automation", AutomationRunStatus.Failed, "changed") {}
            }.exceptionOrNull()
        }

        assertTrue(failure is IllegalStateException)
        assertEquals("Automation execution cannot mutate its own configuration", failure?.message)
        assertEquals(generation, registry.generation("automation"))
    }
}
