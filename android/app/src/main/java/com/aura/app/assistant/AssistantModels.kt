package com.aura.app.assistant

import com.aura.app.automations.AutomationSpec
import com.aura.app.miniapps.MiniAppBundle
import com.google.gson.annotations.SerializedName

data class UserResponse(
    val id: String,
    val email: String,
    val name: String?,
    val role: String?,
    @SerializedName("service_mode") val serviceMode: String = "local"
)

data class StalkyPrincipalResponse(
    @SerializedName("userId") val userId: String,
    val role: String,
    val aal: String? = null,
    @SerializedName("sessionId") val sessionId: String? = null
)

enum class LlmProvider(val wireValue: String, val label: String) {
    Gemini("gemini", "Google"),
    OpenAI("openai", "OpenAI"),
    OpenRouter("openrouter", "OpenRouter");

    companion object {
        fun fromWireValue(value: String?): LlmProvider =
            entries.firstOrNull { it.wireValue == value } ?: Gemini
    }
}

data class ChatMemoryItem(val title: String, val content: String)
data class ChatTodoItem(val title: String, val done: Boolean)
data class ChatAppItem(val label: String, val package_name: String)
data class ChatMiniAppItem(
    val id: String,
    val name: String,
    val intents: List<String> = emptyList(),
    val actions: List<String> = emptyList()
)
data class ChatAutomationItem(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger_type: String,
    val action_types: List<String> = emptyList()
)

data class ChatWidgetAction(
    val id: String? = null,
    val label: String? = null,
    val type: String? = null,
    val payload: Map<String, String>? = null,
    val requires_confirmation: Boolean = false
)

data class ChatWidgetProposal(
    val kind: String? = null,
    val title: String? = null,
    val message: String? = null,
    val details: List<String>? = null,
    val actions: List<ChatWidgetAction>? = null,
    val presentation: String = "compact",
    val content_format: String = "plain_text",
    val content: String? = null,
    val risk: String? = "low",
    val priority: Int = 0,
    val expires_in_minutes: Int = 60,
    val dedupe_key: String? = null
)

data class ChatRequest(
    val message: String,
    val session_id: String? = null,
    val provider: String,
    val api_key: String,
    val model: String,
    val memories: List<ChatMemoryItem> = emptyList(),
    val todos: List<ChatTodoItem> = emptyList(),
    val apps: List<ChatAppItem> = emptyList(),
    val mini_apps: List<ChatMiniAppItem> = emptyList(),
    val automations: List<ChatAutomationItem> = emptyList(),
    val image_base64: String? = null,
    val image_mime_type: String? = null
)
data class ChatAction(
    val type: String,
    val package_name: String? = null,
    val app_query: String? = null,
    val duration_minutes: Int? = null,
    val mini_app_id: String? = null,
    val mini_app_query: String? = null,
    val mini_app_prompt: String? = null,
    val revision_instruction: String? = null,
    val open_after_create: Boolean? = null,
    val action_id: String? = null,
    val record_type: String? = null,
    val values: Map<String, String>? = null,
    val automation_spec: AutomationSpec? = null,
    val widget: ChatWidgetProposal? = null
)
data class ChatResponse(
    val reply: String,
    val session_id: String,
    val emotion: String = "neutral",
    val created_emotion: String? = null,
    val actions: List<ChatAction> = emptyList()
)

data class AgentRunAccepted(
    val run_id: String,
    val session_id: String,
    val state: String
)

data class AgentChildRun(
    val id: String,
    val agent: String,
    val state: String,
    val phase: String,
    val output: String? = null,
    val error: String? = null
)

data class AgentRunResponse(
    val id: String,
    val session_id: String,
    val state: String,
    val phase: String,
    val reply: String? = null,
    val emotion: String = "neutral",
    val created_emotion: String? = null,
    val actions: List<ChatAction> = emptyList(),
    val children: List<AgentChildRun> = emptyList(),
    val error: String? = null
)

data class AssistantRunProgress(
    val runId: String,
    val state: String,
    val phase: String,
    val activeSubagents: Int,
    val mode: AssistantRunMode = AssistantRunMode.Managed
)

enum class AssistantRunMode(val wireValue: String) {
    Managed("managed"),
    Local("local")
}

data class OpenRouterModelsRequest(val api_key: String)
data class OpenRouterModelsResponse(val data: List<OpenRouterModelInfo>)

data class MiniAppBuildRequest(
    val prompt: String,
    val provider: String,
    val api_key: String,
    val model: String,
    val runtime: String = "react"
)
data class MiniAppBuildResponse(val bundle: MiniAppBundle)
data class MiniAppRevisionRequest(
    val instruction: String,
    val currentBundle: MiniAppBundle,
    val recordSample: List<Map<String, Any>> = emptyList(),
    val provider: String,
    val api_key: String,
    val model: String,
    val runtime: String? = null
)
data class MiniAppRevisionResponse(
    val bundle: MiniAppBundle,
    val summary: String,
    val migrationPlan: List<String> = emptyList()
)

data class MemoryCreateRequest(val title: String, val content: String)
data class MemoryResponse(val id: String, val title: String, val content: String, val created_at: String)

data class TodoCreateRequest(val title: String)
data class TodoUpdateRequest(val title: String? = null, val done: Boolean? = null)
data class TodoResponse(val id: String, val title: String, val done: Boolean, val created_at: String)

data class TranscribeRequest(
    val audio_base64: String,
    val mime_type: String = "audio/wav",
    val api_key: String? = null,
    val provider: String? = null
)
data class TranscribeResponse(val text: String)

data class AssistantMessage(val role: MessageRole, val text: String)

enum class MessageRole {
    User,
    Assistant
}
