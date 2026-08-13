package com.aura.app.assistant

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

internal interface LocalProviderGateway {
    suspend fun chat(request: ChatRequest, settings: LlmSettingsState): ChatResponse
    suspend fun transcribe(audioBase64: String, mimeType: String, settings: LlmSettingsState): String
    suspend fun openRouterModels(apiKey: String): List<OpenRouterModelInfo>
}

/** Direct provider transport used only when the user selected backend-free local mode. */
internal class DirectLocalProviderGateway(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : LocalProviderGateway {
    override suspend fun chat(request: ChatRequest, settings: LlmSettingsState): ChatResponse {
        require(request.message.isNotBlank() && request.message.length <= MAX_USER_MESSAGE_CHARS) {
            "Message must contain between 1 and $MAX_USER_MESSAGE_CHARS characters"
        }
        validateInlinePayload(request.image_base64, request.image_mime_type, IMAGE_MIME_TYPES)
        val raw = when (settings.provider) {
            LlmProvider.Gemini -> callGemini(
                apiKey = settings.googleApiKey,
                model = settings.googleModel,
                systemPrompt = localSystemPrompt(request),
                userMessage = request.message,
                imageBase64 = request.image_base64,
                imageMimeType = request.image_mime_type,
                requireJson = true
            )
            LlmProvider.OpenAI -> callOpenAiCompatible(
                baseUrl = "https://api.openai.com/v1/chat/completions",
                apiKey = settings.openAiApiKey,
                model = settings.openAiModel,
                systemPrompt = localSystemPrompt(request),
                userMessage = request.message
            )
            LlmProvider.OpenRouter -> callOpenAiCompatible(
                baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = settings.openRouterApiKey,
                model = settings.openRouterModel,
                systemPrompt = localSystemPrompt(request),
                userMessage = request.message
            )
        }
        return parseLocalAssistantResponse(raw, request.session_id.orEmpty(), gson)
    }

    override suspend fun transcribe(
        audioBase64: String,
        mimeType: String,
        settings: LlmSettingsState
    ): String {
        validateInlinePayload(audioBase64, mimeType, AUDIO_MIME_TYPES)
        return when (settings.provider) {
        LlmProvider.Gemini -> callGemini(
            apiKey = settings.googleApiKey,
            model = settings.googleModel,
            systemPrompt = "Transcribe the supplied audio exactly. Return only the transcript.",
            userMessage = "Transcribe this audio.",
            imageBase64 = audioBase64,
            imageMimeType = mimeType,
            requireJson = false
        ).trim()
        LlmProvider.OpenAI -> transcribeWithOpenAi(audioBase64, mimeType, settings.openAiApiKey)
        LlmProvider.OpenRouter -> throw IllegalStateException("OpenRouter transcription is not supported in local mode")
        }
    }

    override suspend fun openRouterModels(apiKey: String): List<OpenRouterModelInfo> {
        val response = execute(
            Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer ${requiredSecret(apiKey, "OpenRouter")}")
                .get()
                .build(),
            "OpenRouter"
        )
        val data = gson.fromJson(response, JsonObject::class.java)
            .getAsJsonArray("data")
            ?.mapNotNull { item ->
                val value = item.asJsonObject
                val id = value.get("id")?.asString.orEmpty()
                if (id.isBlank()) null else OpenRouterModelInfo(
                    id = id,
                    name = value.get("name")?.asString ?: id
                )
            }
            .orEmpty()
        return data.sortedBy { it.name.lowercase() }
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String,
        imageBase64: String?,
        imageMimeType: String?,
        requireJson: Boolean
    ): String {
        val normalizedModel = model.removePrefix("models/").trim()
        require(normalizedModel.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Gemini model is invalid" }
        val parts = mutableListOf<Map<String, Any>>(mapOf("text" to userMessage))
        if (!imageBase64.isNullOrBlank()) {
            parts += mapOf(
                "inline_data" to mapOf(
                    "mime_type" to (imageMimeType ?: "application/octet-stream"),
                    "data" to imageBase64
                )
            )
        }
        val body = linkedMapOf<String, Any>(
            "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
            "contents" to listOf(mapOf("role" to "user", "parts" to parts))
        )
        if (requireJson) {
            body["generationConfig"] = mapOf("responseMimeType" to "application/json")
        }
        val response = execute(
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent")
                .header("x-goog-api-key", requiredSecret(apiKey, "Google Gemini"))
                .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            "Google Gemini"
        )
        val payload = gson.fromJson(response, JsonObject::class.java)
        return payload.getAsJsonArray("candidates")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.firstOrNull()?.asJsonObject
            ?.get("text")?.asString
            ?: throw IllegalStateException("Google Gemini returned no content")
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): String {
        require(model.isNotBlank() && model.length <= 200) { "Model is required" }
        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userMessage)
            ),
            "response_format" to mapOf("type" to "json_object")
        )
        val response = execute(
            Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer ${requiredSecret(apiKey, "provider")}")
                .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            "AI provider"
        )
        return gson.fromJson(response, JsonObject::class.java)
            .getAsJsonArray("choices")
            ?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString
            ?: throw IllegalStateException("AI provider returned no content")
    }

    private fun transcribeWithOpenAi(audioBase64: String, mimeType: String, apiKey: String): String {
        val audio = runCatching { Base64.getDecoder().decode(audioBase64) }
            .getOrElse { throw IllegalArgumentException("Audio payload is invalid", it) }
        require(audio.size <= 25 * 1024 * 1024) { "Audio is larger than 25 MB" }
        val extension = when (mimeType.lowercase()) {
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/webm" -> "webm"
            else -> "wav"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "gpt-4o-mini-transcribe")
            .addFormDataPart("file", "aura-audio.$extension", audio.toRequestBody(mimeType.toMediaType()))
            .build()
        val response = execute(
            Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer ${requiredSecret(apiKey, "OpenAI")}")
                .post(body)
                .build(),
            "OpenAI"
        )
        return gson.fromJson(response, JsonObject::class.java).get("text")?.asString
            ?: throw IllegalStateException("OpenAI returned no transcript")
    }

    private fun execute(request: Request, providerLabel: String): String = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IllegalStateException("$providerLabel request failed (${response.code})")
        }
        val body = response.body ?: throw IllegalStateException("$providerLabel returned an empty response")
        val contentLength = body.contentLength()
        require(contentLength < 0 || contentLength <= MAX_PROVIDER_RESPONSE_BYTES) {
            "$providerLabel response is too large"
        }
        val source = body.source()
        source.request(MAX_PROVIDER_RESPONSE_BYTES + 1L)
        require(source.buffer.size <= MAX_PROVIDER_RESPONSE_BYTES) {
            "$providerLabel response is too large"
        }
        source.readUtf8()
    }

    private fun requiredSecret(value: String, providerLabel: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("$providerLabel API key is required")

    private fun localSystemPrompt(request: ChatRequest): String = buildString {
        append("You are Aura, an Android assistant running entirely in local mode. ")
        append("Never claim that a server action succeeded. Return only one JSON object with ")
        append("keys reply, emotion, and actions. actions must be an array and may contain only ")
        append("operations supported by the visible app, task, memory, mini-app, and automation context. ")
        append("Use an empty actions array when no action is required.\n")
        append("Memories: ").append(gson.toJson(request.memories.take(40))).append('\n')
        append("Tasks: ").append(gson.toJson(request.todos.take(100))).append('\n')
        append("Apps: ").append(gson.toJson(request.apps.take(250))).append('\n')
        append("Mini apps: ").append(gson.toJson(request.mini_apps.take(100))).append('\n')
        append("Automations: ").append(gson.toJson(request.automations.take(100)))
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_PROVIDER_RESPONSE_BYTES = 1_000_000L
        const val MAX_USER_MESSAGE_CHARS = 32_000
        val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val AUDIO_MIME_TYPES = setOf(
            "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4", "audio/m4a", "audio/webm"
        )
    }
}

internal fun validateInlinePayload(
    base64: String?,
    mimeType: String?,
    allowedMimeTypes: Set<String>
) {
    if (base64.isNullOrBlank()) return
    require(base64.length <= MAX_INLINE_BASE64_CHARS) { "Attachment is larger than 25 MB" }
    val normalizedMimeType = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    require(normalizedMimeType in allowedMimeTypes) { "Attachment type is not supported" }
    require(base64.length % 4 == 0 && BASE64_PATTERN.matches(base64)) {
        "Attachment payload is invalid"
    }
}

private const val MAX_INLINE_BASE64_CHARS = 35_000_000
private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]*={0,2}$")

internal fun parseLocalAssistantResponse(raw: String, sessionId: String, gson: Gson = Gson()): ChatResponse {
    val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val json = runCatching { gson.fromJson(trimmed, JsonObject::class.java) }.getOrNull()
    val reply = json?.get("reply")?.takeIf { it.isJsonPrimitive }?.asString
        ?.take(32_000)
        ?.takeIf { it.isNotBlank() }
        ?: trimmed.take(32_000).ifBlank { "{neutral} No response." }
    val emotion = json?.get("emotion")?.takeIf { it.isJsonPrimitive }?.asString
        ?.take(40)
        ?: "neutral"
    val actions = json?.getAsJsonArray("actions")
        ?.take(16)
        ?.mapNotNull { action -> runCatching { gson.fromJson(action, ChatAction::class.java) }.getOrNull() }
        .orEmpty()
    return ChatResponse(reply = reply, session_id = sessionId, emotion = emotion, actions = actions)
}
