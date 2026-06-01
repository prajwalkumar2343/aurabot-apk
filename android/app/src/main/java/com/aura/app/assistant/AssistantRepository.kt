package com.aura.app.assistant

import com.aura.app.session.SessionStore
import com.aura.app.apps.AppInfo
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppInstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class GuestFeatureException(feature: String) : IllegalStateException("$feature requires login")

class AssistantRepository(
    baseUrl: String,
    private val sessionStore: SessionStore,
    private val localAssistantStore: LocalAssistantStore,
    private val llmSettingsStore: LlmSettingsStore
) {
    private val api: AuraApi

    init {
        val normalizedBaseUrl = baseUrl.trimEnd('/') + "/api/"
        val authInterceptor = Interceptor { chain ->
            val token = kotlinx.coroutines.runBlocking { sessionStore.accessToken() }
            val request = if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            chain.proceed(request)
        }
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logger)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuraApi::class.java)
    }

    suspend fun register(email: String, password: String, name: String?): UserResponse = withContext(Dispatchers.IO) {
        val user = api.register(RegisterRequest(email = email, password = password, name = name?.takeIf { it.isNotBlank() }))
        login(email, password)
        user
    }

    suspend fun login(email: String, password: String): UserResponse = withContext(Dispatchers.IO) {
        val response = api.login(LoginRequest(email = email, password = password))
        sessionStore.setToken(response.access_token)
        UserResponse(response.id, response.email, response.name, response.role)
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
        miniApps: List<MiniAppInstall> = emptyList(),
        miniAppBundles: List<MiniAppBundle> = emptyList()
    ): ChatResponse = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
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
                        intents = bundle?.assistantIntents?.map { it.name }.orEmpty()
                    )
                }
            )
        )
    }

    suspend fun openRouterModels(): List<OpenRouterModelInfo> = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add an OpenRouter API key in Settings")
        }
        api.openRouterModels(OpenRouterModelsRequest(api_key = apiKey)).data
    }

    suspend fun buildMiniApp(prompt: String): MiniAppBundle = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
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
