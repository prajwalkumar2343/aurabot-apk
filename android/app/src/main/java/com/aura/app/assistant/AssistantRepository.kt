package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import com.aura.app.apps.AppInfo
import com.aura.app.automations.AutomationSpec
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppInstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class GuestFeatureException(feature: String) : IllegalStateException("$feature requires login")

class AssistantRepository internal constructor(
    private val api: AuraApi,
    private val sessionStore: AuthTokenStore,
    private val localAssistantStore: AssistantLocalStore,
    private val llmSettingsStore: AssistantLlmSettingsStore
) {
    constructor(
        baseUrl: String,
        sessionStore: AuthTokenStore,
        localAssistantStore: LocalAssistantStore,
        llmSettingsStore: LlmSettingsStore
    ) : this(
        api = createApi(baseUrl, sessionStore),
        sessionStore = sessionStore,
        localAssistantStore = localAssistantStore,
        llmSettingsStore = llmSettingsStore
    )

    suspend fun register(email: String, password: String, name: String?): UserResponse = withContext(Dispatchers.IO) {
        val response = api.register(RegisterRequest(email = email, password = password, name = name?.takeIf { it.isNotBlank() }))
        sessionStore.setTokens(response.access_token, response.refresh_token)
        UserResponse(response.id, response.email, response.name, response.role)
    }

    suspend fun login(email: String, password: String): UserResponse = withContext(Dispatchers.IO) {
        val response = api.login(LoginRequest(email = email, password = password))
        sessionStore.setTokens(response.access_token, response.refresh_token)
        UserResponse(response.id, response.email, response.name, response.role)
    }

    companion object {
        private fun createApi(baseUrl: String, sessionStore: AuthTokenStore): AuraApi {
            val normalizedBaseUrl = baseUrl.trimEnd('/') + "/api/"
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthSessionInterceptor(sessionStore))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuraApi::class.java)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val refreshToken = sessionStore.refreshToken()
        if (!refreshToken.isNullOrBlank()) {
            runCatching { api.logout(RefreshRequest(refreshToken)) }
        }
        sessionStore.clearTokens()
    }

    suspend fun me(): UserResponse = requireLogin("Account") { api.me() }

    suspend fun memories(): List<MemoryResponse> = withContext(Dispatchers.IO) {
        if (sessionStore.accessToken().isNullOrBlank()) {
            localAssistantStore.memories()
        } else {
            api.memories()
        }
    }

    suspend fun createMemory(title: String, content: String): MemoryResponse = withContext(Dispatchers.IO) {
        if (sessionStore.accessToken().isNullOrBlank()) {
            localAssistantStore.createMemory(title, content)
        } else {
            api.createMemory(MemoryCreateRequest(title, content))
        }
    }

    suspend fun todos(): List<TodoResponse> = withContext(Dispatchers.IO) {
        if (sessionStore.accessToken().isNullOrBlank()) {
            localAssistantStore.todos()
        } else {
            api.todos()
        }
    }

    suspend fun createTodo(title: String): TodoResponse = withContext(Dispatchers.IO) {
        if (sessionStore.accessToken().isNullOrBlank()) {
            localAssistantStore.createTodo(title)
        } else {
            api.createTodo(TodoCreateRequest(title))
        }
    }

    suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = withContext(Dispatchers.IO) {
        if (sessionStore.accessToken().isNullOrBlank()) {
            localAssistantStore.updateTodoDone(id, done)
        } else {
            api.updateTodo(id, TodoUpdateRequest(done = done))
        }
    }

    suspend fun chat(
        message: String,
        sessionId: String?,
        apps: List<AppInfo>,
        automations: List<AutomationSpec> = emptyList(),
        miniApps: List<MiniAppInstall> = emptyList(),
        miniAppBundles: List<MiniAppBundle> = emptyList(),
        image_base64: String? = null,
        image_mime_type: String? = null
    ): ChatResponse = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = settings.currentApiKey
        val model = settings.currentModel
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        val memories = localAssistantStore.memories()
        val todos = localAssistantStore.todos()
        api.chat(
            ChatRequest(
                message = message,
                session_id = sessionId ?: UUID.randomUUID().toString(),
                provider = settings.provider.wireValue,
                api_key = apiKey,
                model = model,
                memories = memories.map { ChatMemoryItem(title = it.title, content = it.content) },
                todos = todos.map { ChatTodoItem(title = it.title, done = it.done) },
                apps = apps.map { ChatAppItem(label = it.label, package_name = it.packageName) },
                mini_apps = miniApps.map { install ->
                    val bundle = miniAppBundles.firstOrNull { it.id == install.id }
                    ChatMiniAppItem(
                        id = install.id,
                        name = install.name,
                        intents = bundle?.assistantIntents?.map { it.name }.orEmpty(),
                        actions = bundle?.actions?.map { it.id }.orEmpty()
                    )
                },
                automations = automations.map { automation ->
                    ChatAutomationItem(
                        id = automation.id,
                        name = automation.name,
                        enabled = automation.enabled,
                        trigger_type = automation.trigger.type,
                        action_types = (
                            automation.actions.map { it.type } +
                                automation.flow?.steps.orEmpty().mapNotNull { it.action?.type }
                            ).distinct()
                    )
                },
                image_base64 = image_base64,
                image_mime_type = image_mime_type
            )
        )
    }

    suspend fun transcribe(audioBase64: String, mimeType: String = "audio/wav"): String = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        when (settings.provider) {
            LlmProvider.Gemini -> settings.googleApiKeyError
            LlmProvider.OpenAI -> settings.openAiApiKeyError
            LlmProvider.OpenRouter -> null
        }?.let { throw IllegalStateException(it) }
        val voiceProvider = when {
            settings.provider == LlmProvider.OpenAI && settings.openAiApiKey.isNotBlank() -> LlmProvider.OpenAI
            settings.provider == LlmProvider.Gemini && settings.googleApiKey.isNotBlank() -> LlmProvider.Gemini
            settings.googleApiKey.isNotBlank() -> LlmProvider.Gemini
            settings.openAiApiKey.isNotBlank() -> LlmProvider.OpenAI
            settings.googleApiKeyError != null || settings.openAiApiKeyError != null ->
                throw IllegalStateException("Stored voice API keys could not be read. Re-enter a key in Settings.")
            else -> throw IllegalStateException("Add a Google or OpenAI API key for voice transcription")
        }
        val voiceApiKey = when (voiceProvider) {
            LlmProvider.Gemini -> settings.googleApiKey
            LlmProvider.OpenAI -> settings.openAiApiKey
            LlmProvider.OpenRouter -> ""
        }.trim()
        val response = api.transcribe(
            TranscribeRequest(
                audio_base64 = audioBase64,
                mime_type = mimeType,
                api_key = voiceApiKey,
                provider = voiceProvider.wireValue
            )
        )
        response.text
    }

    suspend fun openRouterModels(): List<OpenRouterModelInfo> = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        settings.openRouterApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add an OpenRouter API key in Settings")
        }
        api.openRouterModels(OpenRouterModelsRequest(api_key = apiKey)).data
    }

    suspend fun buildMiniApp(prompt: String): MiniAppBundle = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = settings.currentApiKey
        val model = settings.currentModel
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        api.buildMiniApp(
            MiniAppBuildRequest(
                prompt = prompt,
                provider = settings.provider.wireValue,
                api_key = apiKey,
                model = model
            )
        ).bundle
    }

    suspend fun reviseMiniApp(
        instruction: String,
        currentBundle: MiniAppBundle,
        recordSample: List<Map<String, Any>>
    ): MiniAppRevisionResponse = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = settings.currentApiKey
        val model = settings.currentModel
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        api.reviseMiniApp(
            MiniAppRevisionRequest(
                instruction = instruction,
                currentBundle = currentBundle,
                recordSample = recordSample,
                provider = settings.provider.wireValue,
                api_key = apiKey,
                model = model,
                runtime = currentBundle.runtime
            )
        )
    }

    suspend fun setProvider(provider: LlmProvider) = llmSettingsStore.setProvider(provider)

    suspend fun setGoogleApiKey(value: String) = llmSettingsStore.setGoogleApiKey(value)

    suspend fun setGoogleModel(value: String) = llmSettingsStore.setGoogleModel(value)

    suspend fun setOpenAiApiKey(value: String) = llmSettingsStore.setOpenAiApiKey(value)

    suspend fun setOpenAiModel(value: String) = llmSettingsStore.setOpenAiModel(value)

    suspend fun setOpenRouterApiKey(value: String) = llmSettingsStore.setOpenRouterApiKey(value)

    suspend fun setOpenRouterModel(value: String) = llmSettingsStore.setOpenRouterModel(value)

    private suspend fun <T> requireLogin(feature: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            if (sessionStore.accessToken().isNullOrBlank()) throw GuestFeatureException(feature)
            block()
        }
}
