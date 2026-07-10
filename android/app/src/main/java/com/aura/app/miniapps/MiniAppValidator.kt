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
    private val safeId = Regex("^[a-z0-9][a-z0-9._-]*$")

    fun validate(bundle: MiniAppBundle): MiniAppBundle {
        val normalizedBundle = MiniAppWidgetPolicy.withDefault(bundle)
        fun requireNamed(value: String, field: String) {
            if (value.trim().isEmpty()) throw MiniAppValidationException("$field is required")
        }

        requireNamed(normalizedBundle.id, "id")
        validateBounds(normalizedBundle)
        if (!safeId.matches(normalizedBundle.id)) {
            throw MiniAppValidationException("Mini app id must use lowercase letters, numbers, dots, underscores, or hyphens")
        }
        requireNamed(normalizedBundle.metadata.name, "metadata.name")
        if (normalizedBundle.runtime !in supportedRuntimes) throw MiniAppValidationException("Unsupported runtime: ${normalizedBundle.runtime}")
        if (normalizedBundle.runtime == "native" && normalizedBundle.screens.isEmpty()) throw MiniAppValidationException("At least one screen is required")
        if (normalizedBundle.runtime == "react") {
            val code = normalizedBundle.codeBundle ?: throw MiniAppValidationException("React mini apps require codeBundle")
            if (code.entry != "App.jsx") throw MiniAppValidationException("React codeBundle entry must be App.jsx")
            if (code.compiledJs.isBlank()) throw MiniAppValidationException("React mini apps require compiledJs")
            if (code.appJsx.length > 30_000) throw MiniAppValidationException("React appJsx is too large")
            if (code.css.length > 16_000) throw MiniAppValidationException("React css is too large")
            if (code.compiledJs.length > 1_500_000) throw MiniAppValidationException("Compiled React mini app is too large")
            code.allowedApis.forEach {
                if (it !in supportedCodeApis) throw MiniAppValidationException("Unsupported React API: $it")
            }
        }
        normalizedBundle.capabilities.forEach {
            if (it !in supportedCapabilities) throw MiniAppValidationException("Unsupported capability: $it")
        }
        requireNamed(normalizedBundle.dataSchema.recordType, "dataSchema.recordType")
        normalizedBundle.dataSchema.fields.forEach {
            if (it.name.isBlank()) throw MiniAppValidationException("Field names are required")
            if (it.type !in supportedFieldTypes) throw MiniAppValidationException("Unsupported field type: ${it.type}")
        }
        if (normalizedBundle.dataSchema.fields.map { it.name }.distinct().size != normalizedBundle.dataSchema.fields.size) {
            throw MiniAppValidationException("Field names must be unique")
        }
        val schemaFieldNames = normalizedBundle.dataSchema.fields.map { it.name }.toSet()
        val actionIds = normalizedBundle.actions.map { it.id }.toSet()
        if (actionIds.size != normalizedBundle.actions.size) throw MiniAppValidationException("Action ids must be unique")
        normalizedBundle.actions.forEach {
            requireNamed(it.id, "action.id")
            if (it.type !in supportedActions) throw MiniAppValidationException("Unsupported action: ${it.type}")
            if (it.type == "create_record") {
                if (it.recordType != "record" && it.recordType != normalizedBundle.dataSchema.recordType) {
                    throw MiniAppValidationException("Unsupported action record type: ${it.recordType}")
                }
                it.values.keys.forEach { fieldName ->
                    if (fieldName !in schemaFieldNames) throw MiniAppValidationException("Unknown action field: $fieldName")
                }
                if (it.values.values.any { value -> value.length > 4_000 }) {
                    throw MiniAppValidationException("Action value is too large: ${it.id}")
                }
            }
        }
        validateWidget(normalizedBundle, actionIds)
        val screenIds = normalizedBundle.screens.map { it.id }.toSet()
        if (screenIds.size != normalizedBundle.screens.size) throw MiniAppValidationException("Screen ids must be unique")
        normalizedBundle.screens.forEach { screen ->
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
        val intentNames = normalizedBundle.assistantIntents.map { it.name }.toSet()
        if (intentNames.size != normalizedBundle.assistantIntents.size) throw MiniAppValidationException("Intent names must be unique")
        normalizedBundle.assistantIntents.forEach { intent ->
            if (intent.name.isBlank()) throw MiniAppValidationException("Intent names are required")
            intent.actionId?.let {
                if (it !in actionIds) throw MiniAppValidationException("Unknown intent action: $it")
            }
            intent.screenId?.let {
                if (it !in screenIds) throw MiniAppValidationException("Unknown intent screen: $it")
            }
            if (intent.utterances.any { it.isBlank() || it.length > 160 }) {
                throw MiniAppValidationException("Invalid intent utterance: ${intent.name}")
            }
        }
        return normalizedBundle
    }

    private fun validateWidget(bundle: MiniAppBundle, actionIds: Set<String>) {
        val widget = bundle.widget ?: throw MiniAppValidationException("Every mini app requires a widget")
        if (widget.type !in MiniAppWidgetPolicy.supportedTypes) {
            throw MiniAppValidationException("Unsupported widget type: ${widget.type}")
        }
        if (widget.metric !in MiniAppWidgetPolicy.supportedMetrics) {
            throw MiniAppValidationException("Unsupported widget metric: ${widget.metric}")
        }
        if (widget.type == "progress" && widget.goal == null) {
            throw MiniAppValidationException("Progress widgets require a goal")
        }
        if (widget.type != "progress" && widget.goal != null) {
            throw MiniAppValidationException("Widget goal is only supported for progress widgets")
        }
        if (widget.goal != null && widget.goal !in 1..1_000_000) {
            throw MiniAppValidationException("Widget goal is out of range")
        }
        if (widget.title.isBlank()) throw MiniAppValidationException("Widget title is required")
        if (widget.description.isBlank()) throw MiniAppValidationException("Widget description is required")
        if (widget.title.length > 60) throw MiniAppValidationException("Widget title is too long")
        if (widget.description.length > 160) throw MiniAppValidationException("Widget description is too long")
        if (widget.actionIds.size > MiniAppWidgetPolicy.maxActions) {
            throw MiniAppValidationException("Widgets support at most ${MiniAppWidgetPolicy.maxActions} actions")
        }
        if (widget.actionIds.distinct().size != widget.actionIds.size) {
            throw MiniAppValidationException("Widget action ids must be unique")
        }
        widget.actionIds.forEach { actionId ->
            if (actionId !in actionIds) throw MiniAppValidationException("Unknown widget action: $actionId")
            if (bundle.actions.first { it.id == actionId }.type != "create_record") {
                throw MiniAppValidationException("Unsafe widget action: $actionId")
            }
        }
    }

    private fun validateBounds(bundle: MiniAppBundle) {
        if (bundle.id.length > 120) throw MiniAppValidationException("Mini app id is too long")
        if (bundle.version !in 1..100_000) throw MiniAppValidationException("Mini app version is out of range")
        if (bundle.metadata.name.length > 80) throw MiniAppValidationException("Mini app name is too long")
        if (bundle.metadata.description.length > 500) throw MiniAppValidationException("Mini app description is too long")
        if (bundle.metadata.category.length > 80) throw MiniAppValidationException("Mini app category is too long")
        if (bundle.dataSchema.fields.size > 60) throw MiniAppValidationException("Too many data fields")
        if (bundle.screens.size > 12) throw MiniAppValidationException("Too many screens")
        if (bundle.actions.size > 60) throw MiniAppValidationException("Too many actions")
        if (bundle.assistantIntents.size > 60) throw MiniAppValidationException("Too many assistant intents")
        if (bundle.capabilities.size > 10) throw MiniAppValidationException("Too many capabilities")
        bundle.dataSchema.fields.forEach { field ->
            if (field.name.length > 80) throw MiniAppValidationException("Field name is too long")
            if ((field.defaultValue?.length ?: 0) > 4_000) throw MiniAppValidationException("Field default is too large: ${field.name}")
        }
        bundle.screens.forEach { screen ->
            if (screen.id.length > 100 || screen.title.length > 160) throw MiniAppValidationException("Screen metadata is too large")
            if (screen.components.size > 40) throw MiniAppValidationException("Too many components on screen: ${screen.id}")
            screen.components.forEach { component ->
                if (component.title.length > 160) throw MiniAppValidationException("Component title is too long")
                if (component.items.size > 20) throw MiniAppValidationException("Too many component items")
                component.items.forEach { item ->
                    if (item.label.length > 160 || (item.value?.length ?: 0) > 500) {
                        throw MiniAppValidationException("Component item is too large")
                    }
                }
            }
        }
        bundle.assistantIntents.forEach { intent ->
            if (intent.name.length > 100) throw MiniAppValidationException("Intent name is too long")
            if (intent.utterances.size > 20) throw MiniAppValidationException("Too many intent utterances: ${intent.name}")
        }
    }
}
