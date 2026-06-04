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
    fun validatesCompleteBuiltInCatalog() {
        val bundles = BuiltInMiniApps.all.map { MiniAppValidator.validate(it) }

        assertEquals(listOf("Habit Tracker", "Focus Planner", "Spend Tracker"), bundles.map { it.metadata.name })
        bundles.forEach { bundle ->
            val componentTypes = bundle.screens.first().components.map { it.type }
            assertEquals(true, bundle.screens.size >= 2)
            assertEquals(true, componentTypes.contains("quick_action_grid"))
            assertEquals(true, componentTypes.contains("timeline"))
            assertEquals(true, componentTypes.contains("chart"))
            assertEquals(true, bundle.screens.flatMap { it.components }.any { it.type == "settings" })
            assertEquals(true, bundle.screens.flatMap { it.components }.any { it.type == "list" })
            assertEquals(true, bundle.screens.flatMap { it.components }.any { it.type == "form" })
        }
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

    @Test
    fun validatesReactMiniAppBundle() {
        val bundle = MiniAppBundle(
            id = "generated.react.notes",
            runtime = "react",
            metadata = MiniAppMetadata("React Notes", "Take notes", "Productivity"),
            capabilities = listOf("local_storage", "assistant_actions", "react_runtime", "scoped_storage"),
            codeBundle = MiniAppCodeBundle(
                entry = "App.jsx",
                appJsx = "export default function App() { return <main />; }",
                compiledJs = "window.__AuraMiniAppMount = function() {};",
                allowedApis = listOf("records")
            )
        )

        assertEquals("react", MiniAppValidator.validate(bundle).runtime)
    }

    @Test
    fun rejectsReactMiniAppWithoutCompiledCode() {
        val bundle = MiniAppBundle(
            id = "generated.react.bad",
            runtime = "react",
            metadata = MiniAppMetadata("Broken"),
            capabilities = listOf("react_runtime"),
            codeBundle = MiniAppCodeBundle(appJsx = "export default function App() { return <main />; }")
        )

        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(bundle)
        }
    }
}
