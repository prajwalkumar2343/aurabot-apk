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
    private val supportedCapabilities = setOf("local_storage", "assistant_actions", "notifications", "react_runtime", "scoped_storage")
    private val supportedFieldTypes = setOf("text", "number", "boolean", "date", "datetime")
    private val supportedRuntimes = setOf("native", "react")
    private val supportedCodeApis = setOf("records")

    fun validate(bundle: MiniAppBundle): MiniAppBundle {
        fun requireNamed(value: String, field: String) {
            if (value.trim().isEmpty()) throw MiniAppValidationException("$field is required")
        }

        requireNamed(bundle.id, "id")
        requireNamed(bundle.metadata.name, "metadata.name")
        if (bundle.runtime !in supportedRuntimes) throw MiniAppValidationException("Unsupported runtime: ${bundle.runtime}")
        if (bundle.runtime == "native" && bundle.screens.isEmpty()) throw MiniAppValidationException("At least one screen is required")
        if (bundle.runtime == "react") {
            val code = bundle.codeBundle ?: throw MiniAppValidationException("React mini apps require codeBundle")
            if (code.entry != "App.jsx") throw MiniAppValidationException("React codeBundle entry must be App.jsx")
            if (code.compiledJs.isBlank()) throw MiniAppValidationException("React mini apps require compiledJs")
            code.allowedApis.forEach {
                if (it !in supportedCodeApis) throw MiniAppValidationException("Unsupported React API: $it")
            }
        }
        bundle.capabilities.forEach {
            if (it !in supportedCapabilities) throw MiniAppValidationException("Unsupported capability: $it")
        }
        requireNamed(bundle.dataSchema.recordType, "dataSchema.recordType")
        bundle.dataSchema.fields.forEach {
            if (it.name.isBlank()) throw MiniAppValidationException("Field names are required")
            if (it.type !in supportedFieldTypes) throw MiniAppValidationException("Unsupported field type: ${it.type}")
        }
        if (bundle.dataSchema.fields.map { it.name }.distinct().size != bundle.dataSchema.fields.size) {
            throw MiniAppValidationException("Field names must be unique")
        }
        val schemaFieldNames = bundle.dataSchema.fields.map { it.name }.toSet()
        val actionIds = bundle.actions.map { it.id }.toSet()
        if (actionIds.size != bundle.actions.size) throw MiniAppValidationException("Action ids must be unique")
        bundle.actions.forEach {
            requireNamed(it.id, "action.id")
            if (it.type !in supportedActions) throw MiniAppValidationException("Unsupported action: ${it.type}")
            if (it.type == "create_record") {
                if (it.recordType != "record" && it.recordType != bundle.dataSchema.recordType) {
                    throw MiniAppValidationException("Unsupported action record type: ${it.recordType}")
                }
                it.values.keys.forEach { fieldName ->
                    if (fieldName !in schemaFieldNames) throw MiniAppValidationException("Unknown action field: $fieldName")
                }
            }
        }
        val screenIds = bundle.screens.map { it.id }.toSet()
        if (screenIds.size != bundle.screens.size) throw MiniAppValidationException("Screen ids must be unique")
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
        val intentNames = bundle.assistantIntents.map { it.name }.toSet()
        if (intentNames.size != bundle.assistantIntents.size) throw MiniAppValidationException("Intent names must be unique")
        bundle.assistantIntents.forEach { intent ->
            if (intent.name.isBlank()) throw MiniAppValidationException("Intent names are required")
            intent.actionId?.let {
                if (it !in actionIds) throw MiniAppValidationException("Unknown intent action: $it")
            }
            intent.screenId?.let {
                if (it !in screenIds) throw MiniAppValidationException("Unknown intent screen: $it")
            }
        }
        return bundle
    }
}
