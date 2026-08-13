package com.aura.app.automations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationAssistantDraftTest {
    @Test
    fun modelAuthoredAutomationCannotOverwriteOrArmAnExistingRule() {
        val draft = AutomationSpec(
            id = "existing-rule",
            name = "LLM rule",
            enabled = true,
            actions = listOf(AutomationAction(type = AutomationActionTypes.Notify)),
            createdBy = "user",
            createdAt = 10L,
            updatedAt = 20L
        ).toAssistantDraft()

        assertTrue(draft.id.isBlank())
        assertEquals(false, draft.enabled)
        assertEquals("assistant", draft.createdBy)
        assertEquals(0L, draft.createdAt)
        assertEquals(0L, draft.updatedAt)
    }
}
