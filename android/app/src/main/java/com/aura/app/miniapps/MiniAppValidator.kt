package com.aura.app.miniapps

class MiniAppValidationException(message: String) : IllegalArgumentException(message)

object MiniAppValidator {
    private val supportedComponents = setOf(
        "dashboard_block",
        "quick_action_grid",
        "timeline",
        "progress_ring",
        "streak_view",
        "chart",
        "form",
        "list",
        "bottom_sheet",
        "button",
        "slider",
        "settings"
    )
    private val supportedActions = setOf("create_record", "query_records", "open_screen", "update_record", "delete_record")
    private val supportedCapabilities = setOf("local_storage", "assistant_actions", "notifications")
    private val supportedFieldTypes = setOf("text", "number", "boolean", "date", "datetime")

    fun validate(bundle: MiniAppBundle): MiniAppBundle {
        fun requireNamed(value: String, field: String) {
            if (value.trim().isEmpty()) throw MiniAppValidationException("$field is required")
        }

        requireNamed(bundle.id, "id")
        requireNamed(bundle.metadata.name, "metadata.name")
        if (bundle.screens.isEmpty()) throw MiniAppValidationException("At least one screen is required")
        bundle.capabilities.forEach {
            if (it !in supportedCapabilities) throw MiniAppValidationException("Unsupported capability: $it")
        }
        bundle.dataSchema.fields.forEach {
            if (it.name.isBlank()) throw MiniAppValidationException("Field names are required")
            if (it.type !in supportedFieldTypes) throw MiniAppValidationException("Unsupported field type: ${it.type}")
        }
        val actionIds = bundle.actions.map { it.id }.toSet()
        bundle.actions.forEach {
            requireNamed(it.id, "action.id")
            if (it.type !in supportedActions) throw MiniAppValidationException("Unsupported action: ${it.type}")
        }
        bundle.screens.forEach { screen ->
            requireNamed(screen.id, "screen.id")
            screen.components.forEach { component ->
                if (component.type !in supportedComponents) {
                    throw MiniAppValidationException("Unsupported component: ${component.type}")
                }
                component.actionId?.let {
                    if (it !in actionIds) throw MiniAppValidationException("Unknown action: $it")
                }
                component.items.forEach { item ->
                    item.actionId?.let {
                        if (it !in actionIds) throw MiniAppValidationException("Unknown action: $it")
                    }
                }
            }
        }
        bundle.assistantIntents.forEach { intent ->
            if (intent.name.isBlank()) throw MiniAppValidationException("Intent names are required")
            intent.actionId?.let {
                if (it !in actionIds) throw MiniAppValidationException("Unknown intent action: $it")
            }
        }
        return bundle
    }
}
