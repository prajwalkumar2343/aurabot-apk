package com.aura.app.assistant

import com.aura.app.miniapps.MiniAppBundle

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val name: String? = null)
data class LoginResponse(
    val id: String,
    val email: String,
    val name: String?,
    val role: String?,
    val access_token: String
)

data class UserResponse(val id: String, val email: String, val name: String?, val role: String?)

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
    val values: Map<String, String>? = null
)
data class ChatResponse(
    val reply: String,
    val session_id: String,
    val actions: List<ChatAction> = emptyList()
)

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
