package com.aura.app.dreams

import com.aura.app.automations.AutomationAction
import com.aura.app.automations.AutomationActionTypes
import com.aura.app.automations.AutomationSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DreamAutomationRevisionTest {
    @Test
    fun `revision ignores persistence timestamps`() {
        val original = automation().copy(createdAt = 10L, updatedAt = 20L)
        val refreshed = original.copy(createdAt = 1_000L, updatedAt = 2_000L)

        assertEquals(
            DreamAutomationRevision.compute(original),
            DreamAutomationRevision.compute(refreshed)
        )
    }

    @Test
    fun `revision changes when behavior changes`() {
        val original = automation()
        val changed = original.copy(
            actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, title = "Different"))
        )

        assertNotEquals(
            DreamAutomationRevision.compute(original),
            DreamAutomationRevision.compute(changed)
        )
    }

    private fun automation() = AutomationSpec(
        id = "send-eta",
        name = "Send ETA",
        actions = listOf(AutomationAction(type = AutomationActionTypes.Notify, title = "Ready"))
    )
}
