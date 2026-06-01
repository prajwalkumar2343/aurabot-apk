package com.aura.app.miniapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniAppValidatorTest {
    @Test
    fun validatesBuiltInHabitTracker() {
        val bundle = MiniAppValidator.validate(BuiltInMiniApps.habitTracker)

        assertEquals("builtin.habit_tracker", bundle.id)
        assertEquals("Habit Tracker", bundle.metadata.name)
    }

    @Test
    fun rejectsUnsupportedComponentsAndCapabilities() {
        val badComponent = BuiltInMiniApps.habitTracker.copy(
            screens = listOf(
                MiniAppScreen("bad", "Bad", listOf(MiniAppComponent("webview", "Nope")))
            )
        )
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(badComponent)
        }

        val badCapability = BuiltInMiniApps.habitTracker.copy(capabilities = listOf("execute_code"))
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(badCapability)
        }
    }
}
