package com.aura.app.voice

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun systemRestoreActionsHandleBootAndWallClockChangesOnly() {
        assertTrue(SystemRestoreActions.handles(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(SystemRestoreActions.handles(Intent.ACTION_TIME_CHANGED))
        assertTrue(SystemRestoreActions.handles(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(!SystemRestoreActions.handles(Intent.ACTION_SCREEN_ON))
        assertTrue(!SystemRestoreActions.handles(null))
    }

    @Test
    fun bootRestoreRunsAutomationsWhenListeningDoesNotRestore() = runTest {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            canRecord = true,
            canStartListeningFromBoot = true,
            readListeningEnabled = { false },
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }

    @Test
    fun bootRestoreStartsListeningWhenAllowedAndStillRestoresAutomations() = runTest {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            canRecord = true,
            canStartListeningFromBoot = true,
            readListeningEnabled = { true },
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("listening", "automations"), events)
    }

    @Test
    fun bootRestoreSkipsListeningWhenForegroundMicrophoneStartIsRestricted() = runTest {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            canRecord = true,
            canStartListeningFromBoot = false,
            readListeningEnabled = { true },
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }

    @Test
    fun bootRestoreSkipsPreferenceReadWithoutMicrophonePermission() = runTest {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            canRecord = false,
            canStartListeningFromBoot = true,
            readListeningEnabled = { events += "preference"; true },
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }

    @Test
    fun clockChangeRestoresAutomationsWithoutReadingListeningState() = runTest {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            restoreListening = false,
            canRecord = true,
            canStartListeningFromBoot = true,
            readListeningEnabled = { events += "preference"; true },
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }

    @Test
    fun bootRestoreStillRestoresAutomationsAfterPreferenceFailure() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            BootRestoreCoordinator.handle(
                canRecord = true,
                canStartListeningFromBoot = true,
                readListeningEnabled = { error("preferences unavailable") },
                startListening = { events += "listening" },
                restoreAutomations = { events += "automations" }
            )
        }.exceptionOrNull()

        assertEquals("preferences unavailable", failure?.message)
        assertEquals(listOf("automations"), events)
    }

    @Test
    fun bootRestoreStillRestoresAutomationsAfterListeningFailure() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            BootRestoreCoordinator.handle(
                canRecord = true,
                canStartListeningFromBoot = true,
                readListeningEnabled = { true },
                startListening = { events += "listening"; error("voice unavailable") },
                restoreAutomations = { events += "automations" }
            )
        }.exceptionOrNull()

        assertEquals("voice unavailable", failure?.message)
        assertEquals(listOf("listening", "automations"), events)
    }

    @Test
    fun bootRestoreAggregatesIndependentRecoveryFailures() = runTest {
        val failure = runCatching {
            BootRestoreCoordinator.handle(
                canRecord = true,
                canStartListeningFromBoot = true,
                readListeningEnabled = { true },
                startListening = { error("voice unavailable") },
                restoreAutomations = { error("automation storage unavailable") }
            )
        }.exceptionOrNull()

        assertEquals("voice unavailable", failure?.message)
        assertEquals(listOf("automation storage unavailable"), failure?.suppressed?.map { it.message })
    }

    @Test
    fun bootRestorePropagatesCancellationWithoutStartingRecovery() = runTest {
        val events = mutableListOf<String>()

        val failure = runCatching {
            BootRestoreCoordinator.handle(
                canRecord = true,
                canStartListeningFromBoot = true,
                readListeningEnabled = { throw CancellationException("cancelled") },
                startListening = { events += "listening" },
                restoreAutomations = { events += "automations" }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(events.isEmpty())
    }
}
