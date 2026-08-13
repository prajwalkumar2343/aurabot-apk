package com.aura.app.automations

/** Converts untrusted model-authored configuration into a new, inert local draft. */
internal fun AutomationSpec.toAssistantDraft(): AutomationSpec = copy(
    id = "",
    enabled = false,
    createdBy = "assistant",
    createdAt = 0L,
    updatedAt = 0L
)
