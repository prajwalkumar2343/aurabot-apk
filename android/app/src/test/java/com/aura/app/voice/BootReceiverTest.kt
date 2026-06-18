package com.aura.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class BootReceiverTest {
    @Test
    fun bootRestoreRunsAutomationsWhenListeningDoesNotRestore() {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            shouldRestoreListening = false,
            canStartListeningFromBoot = true,
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }

    @Test
    fun bootRestoreStartsListeningWhenAllowedAndStillRestoresAutomations() {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            shouldRestoreListening = true,
            canStartListeningFromBoot = true,
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("listening", "automations"), events)
    }

    @Test
    fun bootRestoreSkipsListeningWhenForegroundMicrophoneStartIsRestricted() {
        val events = mutableListOf<String>()

        BootRestoreCoordinator.handle(
            shouldRestoreListening = true,
            canStartListeningFromBoot = false,
            startListening = { events += "listening" },
            restoreAutomations = { events += "automations" }
        )

        assertEquals(listOf("automations"), events)
    }
}
