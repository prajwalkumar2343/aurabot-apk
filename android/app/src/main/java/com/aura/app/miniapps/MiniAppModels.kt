package com.aura.app.miniapps

data class MiniAppBundle(
    val id: String,
    val version: Int = 1,
    val metadata: MiniAppMetadata,
    val theme: MiniAppTheme = MiniAppTheme(),
    val icon: MiniAppIcon = MiniAppIcon(),
    val dataSchema: MiniAppDataSchema = MiniAppDataSchema(),
    val screens: List<MiniAppScreen> = emptyList(),
    val actions: List<MiniAppAction> = emptyList(),
    val assistantIntents: List<MiniAppAssistantIntent> = emptyList(),
    val capabilities: List<String> = listOf("local_storage")
)

data class MiniAppMetadata(
    val name: String,
    val description: String = "",
    val category: String = "Tool",
    val builtIn: Boolean = false
)

data class MiniAppTheme(
    val primary: String = "#4F46E5",
    val secondary: String = "#14B8A6",
    val surface: String = "#111827"
)

data class MiniAppIcon(
    val type: String = "initial",
    val value: String = "A",
    val background: String = "#4F46E5"
)

data class MiniAppDataSchema(
    val recordType: String = "record",
    val fields: List<MiniAppField> = emptyList()
)

data class MiniAppField(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val defaultValue: String? = null
)

data class MiniAppScreen(
    val id: String,
    val title: String,
    val components: List<MiniAppComponent> = emptyList()
)

data class MiniAppComponent(
    val type: String,
    val title: String = "",
    val actionId: String? = null,
    val source: String? = null,
    val metric: String? = null,
    val items: List<MiniAppComponentItem> = emptyList()
)

data class MiniAppComponentItem(
    val label: String,
    val actionId: String? = null,
    val value: String? = null
)

data class MiniAppAction(
    val id: String,
    val type: String,
    val recordType: String = "record",
    val values: Map<String, String> = emptyMap()
)

data class MiniAppAssistantIntent(
    val name: String,
    val utterances: List<String> = emptyList(),
    val actionId: String? = null,
    val screenId: String? = null
)

data class MiniAppInstall(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val icon: MiniAppIcon,
    val builtIn: Boolean,
    val installedAt: Long
)

data class MiniAppRecord(
    val id: String,
    val miniAppId: String,
    val recordType: String,
    val values: Map<String, String>,
    val createdAt: Long,
    val updatedAt: Long
)
