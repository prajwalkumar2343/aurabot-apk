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

        assertEquals(listOf("Habit Tracker", "Focus Planner", "Spend Tracker", "Field Notes"), bundles.map { it.metadata.name })
        bundles.filter { it.runtime == "native" }.forEach { bundle ->
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
    fun validatesBuiltInReactSmokeApp() {
        val bundle = MiniAppValidator.validate(BuiltInMiniApps.fieldNotesReact)

        assertEquals("builtin.react_field_notes", bundle.id)
        assertEquals("react", bundle.runtime)
        assertEquals("Field Notes", bundle.metadata.name)
        assertEquals("field_note", bundle.dataSchema.recordType)
        assertEquals(listOf("local_storage", "assistant_actions", "react_runtime", "scoped_storage"), bundle.capabilities)
        assertEquals(listOf("records"), bundle.codeBundle?.allowedApis)
        assertEquals(true, bundle.codeBundle?.compiledJs?.contains("window.__AuraMiniAppMount") == true)
        assertEquals(true, bundle.codeBundle?.compiledJs?.contains("aura.records.create") == true)
        assertEquals(true, bundle.codeBundle?.compiledJs?.contains("aura.records.update") == true)
        assertEquals(true, bundle.codeBundle?.compiledJs?.contains("aura.records.delete") == true)
        assertEquals(true, bundle.codeBundle?.compiledJs?.contains("data-view") == true)
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
    fun rejectsDuplicateIdentifiers() {
        val base = BuiltInMiniApps.habitTracker

        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(
                base.copy(dataSchema = base.dataSchema.copy(fields = base.dataSchema.fields + base.dataSchema.fields.first()))
            )
        }
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(base.copy(actions = base.actions + base.actions.first()))
        }
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(base.copy(screens = base.screens + base.screens.first()))
        }
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(base.copy(assistantIntents = base.assistantIntents + base.assistantIntents.first()))
        }
    }

    @Test
    fun rejectsAssistantIntentWithUnknownScreen() {
        val base = BuiltInMiniApps.habitTracker

        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(
                base.copy(
                    assistantIntents = base.assistantIntents + MiniAppAssistantIntent(
                        name = "open_missing",
                        utterances = listOf("open missing"),
                        screenId = "missing_screen"
                    )
                )
            )
        }
    }

    @Test
    fun rejectsCreateActionWithUnknownRecordTypeOrField() {
        val base = BuiltInMiniApps.habitTracker

        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(
                base.copy(
                    actions = listOf(
                        MiniAppAction("bad_type", "create_record", recordType = "expense", values = mapOf("habit" to "Water"))
                    )
                )
            )
        }
        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(
                base.copy(
                    actions = listOf(
                        MiniAppAction("bad_field", "create_record", values = mapOf("habit" to "Water", "mood" to "Bright"))
                    )
                )
            )
        }
    }

    @Test
    fun rejectsBlankRecordType() {
        val base = BuiltInMiniApps.habitTracker

        assertThrows(MiniAppValidationException::class.java) {
            MiniAppValidator.validate(base.copy(dataSchema = base.dataSchema.copy(recordType = " ")))
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
