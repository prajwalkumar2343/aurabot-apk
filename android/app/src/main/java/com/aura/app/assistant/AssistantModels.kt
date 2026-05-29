package com.aura.app.assistant

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(
    val id: String,
    val email: String,
    val name: String?,
    val role: String?,
    val access_token: String
)

data class UserResponse(val id: String, val email: String, val name: String?, val role: String?)

data class ChatRequest(val message: String, val session_id: String? = null)
data class ChatResponse(val reply: String, val session_id: String)

data class MemoryCreateRequest(val title: String, val content: String)
data class MemoryResponse(val id: String, val title: String, val content: String, val created_at: String)

data class TodoCreateRequest(val title: String)
data class TodoUpdateRequest(val title: String? = null, val done: Boolean? = null)
data class TodoResponse(val id: String, val title: String, val done: Boolean, val created_at: String)

data class TranscribeRequest(val audio_base64: String, val mime_type: String = "audio/m4a")
data class TranscribeResponse(val text: String)

data class AssistantMessage(val role: MessageRole, val text: String)

enum class MessageRole {
    User,
    Assistant
}
