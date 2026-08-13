package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import com.aura.app.apps.AppInfo
import com.aura.app.automations.AutomationSpec
import com.aura.app.automations.hasRetiredAutomationActions
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppInstall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class GuestFeatureException(feature: String) : IllegalStateException("$feature requires login")
class StalkyCloudUnavailableException : IllegalStateException(
    "Stalky Cloud is not configured for this build."
)

class AssistantRepository internal constructor(
    private val api: AuraApi?,
    private val sessionStore: AuthTokenStore,
    private val localAssistantStore: AssistantLocalStore,
    private val llmSettingsStore: AssistantLlmSettingsStore,
    private val localProviderGateway: LocalProviderGateway? = null,
    private val supabaseAuth: SupabaseAuthTransport? = null
) {
    private data class CloudDependencies(
        val api: AuraApi,
        val auth: SupabaseAuthTransport
    )

    private constructor(
        cloudDependencies: CloudDependencies?,
        sessionStore: AuthTokenStore,
        localAssistantStore: AssistantLocalStore,
        llmSettingsStore: AssistantLlmSettingsStore,
        localProviderGateway: LocalProviderGateway
    ) : this(
        api = cloudDependencies?.api,
        sessionStore = sessionStore,
        localAssistantStore = localAssistantStore,
        llmSettingsStore = llmSettingsStore,
        localProviderGateway = localProviderGateway,
        supabaseAuth = cloudDependencies?.auth
    )

    internal constructor(
        cloudConfiguration: StalkyCloudConfiguration?,
        sessionStore: AuthTokenStore,
        localAssistantStore: AssistantLocalStore,
        llmSettingsStore: AssistantLlmSettingsStore,
        localProviderGateway: LocalProviderGateway
    ) : this(
        cloudDependencies = cloudConfiguration?.let {
            createCloudDependencies(it, sessionStore)
        },
        sessionStore = sessionStore,
        localAssistantStore = localAssistantStore,
        llmSettingsStore = llmSettingsStore,
        localProviderGateway = localProviderGateway
    )

    suspend fun register(email: String, password: String, name: String?): UserResponse = withContext(Dispatchers.IO) {
        userFromSession(requireSupabaseAuth().signUp(email, password, name))
    }

    suspend fun login(email: String, password: String): UserResponse = withContext(Dispatchers.IO) {
        userFromSession(requireSupabaseAuth().signInWithPassword(email, password))
    }

    suspend fun googleSignInChallenge(): String = withContext(Dispatchers.IO) {
        requireSupabaseAuth()
        createGoogleNonce()
    }

    suspend fun loginWithGoogle(idToken: String, nonce: String): UserResponse = withContext(Dispatchers.IO) {
        userFromSession(requireSupabaseAuth().exchangeGoogleIdToken(idToken, nonce))
    }

    companion object {
        private const val MAX_AGENT_RUN_POLLS = 90

        internal fun agentRunPollDelayMillis(attempt: Int): Long = when {
            attempt < 8 -> 250L
            attempt < 20 -> 500L
            attempt < 50 -> 1_000L
            else -> 2_000L
        }

        private fun createApi(
            baseUrl: String,
            sessionStore: AuthTokenStore,
            authTransport: SupabaseAuthTransport
        ): AuraApi {
            val normalizedBaseUrl = baseUrl.trimEnd('/') + "/api/"
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthSessionInterceptor(sessionStore, authTransport))
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

        private fun createCloudDependencies(
            configuration: StalkyCloudConfiguration,
            sessionStore: AuthTokenStore
        ): CloudDependencies {
            val auth = SupabaseAuthClient.create(configuration)
            return CloudDependencies(
                api = createApi(configuration.apiUrl, sessionStore, auth),
                auth = auth
            )
        }

        internal fun createGoogleNonce(): String {
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val accessToken = sessionStore.accessToken()
        if (sessionStore.serviceMode() == "managed" &&
            !accessToken.isNullOrBlank() &&
            supabaseAuth != null
        ) {
            runCatching { supabaseAuth.logout(accessToken) }
        }
        sessionStore.clearTokens()
    }

    suspend fun me(): UserResponse = requireLogin("Account") {
        val principal = requireCloudApi().me()
        UserResponse(
            id = principal.userId,
            email = "",
            name = null,
            role = principal.role,
            serviceMode = sessionStore.serviceMode()
        )
    }

    suspend fun memories(): List<MemoryResponse> = withContext(Dispatchers.IO) {
        if (sessionStore.serviceMode() != "managed") {
            localAssistantStore.memories()
        } else {
            requireCloudApi().memories()
        }
    }

    suspend fun createMemory(title: String, content: String): MemoryResponse = withContext(Dispatchers.IO) {
        if (sessionStore.serviceMode() != "managed") {
            localAssistantStore.createMemory(title, content)
        } else {
            requireCloudApi().createMemory(MemoryCreateRequest(title, content))
        }
    }

    suspend fun todos(): List<TodoResponse> = withContext(Dispatchers.IO) {
        if (sessionStore.serviceMode() != "managed") {
            localAssistantStore.todos()
        } else {
            requireCloudApi().todos()
        }
    }

    suspend fun createTodo(title: String): TodoResponse = withContext(Dispatchers.IO) {
        if (sessionStore.serviceMode() != "managed") {
            localAssistantStore.createTodo(title)
        } else {
            requireCloudApi().createTodo(TodoCreateRequest(title))
        }
    }

    suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = withContext(Dispatchers.IO) {
        if (sessionStore.serviceMode() != "managed") {
            localAssistantStore.updateTodoDone(id, done)
        } else {
            requireCloudApi().updateTodo(id, TodoUpdateRequest(done = done))
        }
    }

    suspend fun chat(
        message: String,
        sessionId: String?,
        memories: List<MemoryResponse>,
        todos: List<TodoResponse>,
        apps: List<AppInfo>,
        automations: List<AutomationSpec> = emptyList(),
        miniApps: List<MiniAppInstall> = emptyList(),
        miniAppBundles: List<MiniAppBundle> = emptyList(),
        image_base64: String? = null,
        image_mime_type: String? = null,
        onRunProgress: suspend (AssistantRunProgress) -> Unit = {}
    ): ChatResponse = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        val managed = sessionStore.serviceMode() == "managed"
        if (!managed) settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = if (managed) "" else settings.currentApiKey
        val model = if (managed) DEFAULT_GEMINI_MODEL else settings.currentModel
        val provider = if (managed) LlmProvider.Gemini else settings.provider
        if (!managed && apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        val request = ChatRequest(
                message = message,
                session_id = sessionId ?: UUID.randomUUID().toString(),
                provider = provider.wireValue,
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
                        action_types = if (automation.hasRetiredAutomationActions()) {
                            listOf("unsupported_legacy_cross_app")
                        } else {
                            (
                                automation.actions.map { it.type } +
                                    automation.flow?.steps.orEmpty().mapNotNull { it.action?.type }
                                ).distinct()
                        }
                    )
                },
                image_base64 = image_base64,
                image_mime_type = image_mime_type
            )
        if (!managed) {
            val localRunId = "local-${UUID.randomUUID()}"
            onRunProgress(
                AssistantRunProgress(
                    runId = localRunId,
                    state = "queued",
                    phase = "admitted",
                    activeSubagents = 0,
                    mode = AssistantRunMode.Local
                )
            )
            onRunProgress(
                AssistantRunProgress(
                    runId = localRunId,
                    state = "running",
                    phase = "planning",
                    activeSubagents = 0,
                    mode = AssistantRunMode.Local
                )
            )
            try {
                val response = requireNotNull(localProviderGateway) {
                    "Local provider gateway is not configured"
                }.chat(request, settings)
                onRunProgress(
                    AssistantRunProgress(
                        runId = localRunId,
                        state = "completed",
                        phase = "completed",
                        activeSubagents = 0,
                        mode = AssistantRunMode.Local
                    )
                )
                return@withContext response
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onRunProgress(
                    AssistantRunProgress(
                        runId = localRunId,
                        state = "failed",
                        phase = "failed",
                        activeSubagents = 0,
                        mode = AssistantRunMode.Local
                    )
                )
                throw error
            }
        }
        val accepted = requireCloudApi().startAssistantRun(UUID.randomUUID().toString(), request)
        onRunProgress(
            AssistantRunProgress(
                runId = accepted.run_id,
                state = accepted.state,
                phase = "admitted",
                activeSubagents = 0,
                mode = AssistantRunMode.Managed
            )
        )
        for (attempt in 0 until MAX_AGENT_RUN_POLLS) {
            val run = requireCloudApi().assistantRun(accepted.run_id)
            onRunProgress(
                AssistantRunProgress(
                    runId = run.id,
                    state = run.state,
                    phase = run.phase,
                    activeSubagents = run.children.count {
                        it.state == "queued" || it.state == "running"
                    },
                    mode = AssistantRunMode.Managed
                )
            )
            when (run.state) {
                "completed" -> return@withContext ChatResponse(
                    reply = run.reply ?: "{neutral} Done.",
                    session_id = run.session_id,
                    emotion = run.emotion,
                    created_emotion = run.created_emotion,
                    actions = run.actions
                )
                "failed", "interrupted", "cancelled" -> throw IllegalStateException(
                    run.error ?: "Assistant run ${run.state}"
                )
            }
            if (attempt < MAX_AGENT_RUN_POLLS - 1) delay(agentRunPollDelayMillis(attempt))
        }
        throw IllegalStateException("Assistant run timed out")
    }

    suspend fun managedRun(runId: String): AgentRunResponse = withContext(Dispatchers.IO) {
        check(sessionStore.serviceMode() == "managed") {
            "Managed assistant runs are unavailable in local mode"
        }
        requireCloudApi().assistantRun(runId)
    }

    suspend fun transcribe(audioBase64: String, mimeType: String = "audio/wav"): String = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        if (sessionStore.serviceMode() == "managed") {
            return@withContext requireCloudApi().transcribe(
                TranscribeRequest(
                    audio_base64 = audioBase64,
                    mime_type = mimeType,
                    api_key = null,
                    provider = LlmProvider.Gemini.wireValue
                )
            ).text
        }
        return@withContext requireNotNull(localProviderGateway) {
            "Local provider gateway is not configured"
        }.transcribe(audioBase64, mimeType, settings)
    }

    suspend fun openRouterModels(): List<OpenRouterModelInfo> = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        settings.openRouterApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = settings.openRouterApiKey.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Add an OpenRouter API key in Settings")
        }
        if (sessionStore.serviceMode() == "managed") {
            requireCloudApi().openRouterModels(OpenRouterModelsRequest(api_key = apiKey)).data
        } else {
            requireNotNull(localProviderGateway) { "Local provider gateway is not configured" }
                .openRouterModels(apiKey)
        }
    }

    suspend fun buildMiniApp(prompt: String): MiniAppBundle = withContext(Dispatchers.IO) {
        val settings = llmSettingsStore.state.first()
        val managed = sessionStore.serviceMode() == "managed"
        if (!managed) settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = if (managed) "" else settings.currentApiKey
        val model = if (managed) DEFAULT_GEMINI_MODEL else settings.currentModel
        val provider = if (managed) LlmProvider.Gemini else settings.provider
        if (!managed) {
            throw IllegalStateException("Mini-app generation requires Continue with Google")
        }
        if (!managed && apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        requireCloudApi().buildMiniApp(
            MiniAppBuildRequest(
                prompt = prompt,
                provider = provider.wireValue,
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
        val managed = sessionStore.serviceMode() == "managed"
        if (!managed) settings.currentApiKeyError?.let { throw IllegalStateException(it) }
        val apiKey = if (managed) "" else settings.currentApiKey
        val model = if (managed) DEFAULT_GEMINI_MODEL else settings.currentModel
        val provider = if (managed) LlmProvider.Gemini else settings.provider
        if (!managed) {
            throw IllegalStateException("Mini-app revision requires Continue with Google")
        }
        if (!managed && apiKey.isBlank()) {
            throw IllegalStateException("Add a ${settings.provider.label} API key in Settings")
        }
        if (model.isBlank()) {
            throw IllegalStateException("Choose a ${settings.provider.label} model in Settings")
        }
        requireCloudApi().reviseMiniApp(
            MiniAppRevisionRequest(
                instruction = instruction,
                currentBundle = currentBundle,
                recordSample = recordSample,
                provider = provider.wireValue,
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

    private fun requireCloudApi(): AuraApi = api ?: throw StalkyCloudUnavailableException()

    private fun requireSupabaseAuth(): SupabaseAuthTransport =
        supabaseAuth ?: throw StalkyCloudUnavailableException()

    private suspend fun userFromSession(session: SupabaseAuthSession): UserResponse {
        val authenticatedSession = session.requireTokens()
        val user = authenticatedSession.user ?: throw IllegalStateException(
            "Supabase returned a session without a user."
        )
        val id = user.id?.takeIf { it.isNotBlank() } ?: throw IllegalStateException(
            "Supabase returned a session without a user id."
        )
        val email = user.email?.takeIf { it.isNotBlank() } ?: throw IllegalStateException(
            "Supabase returned a session without an email."
        )
        sessionStore.setAuthenticatedSession(
            authenticatedSession.access_token,
            authenticatedSession.refresh_token,
            "managed"
        )
        return UserResponse(
            id = id,
            email = email,
            name = user.userMetadata?.fullName ?: user.userMetadata?.name,
            role = "authenticated",
            serviceMode = "managed"
        )
    }

    private suspend fun <T> requireLogin(feature: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            if (sessionStore.accessToken().isNullOrBlank()) throw GuestFeatureException(feature)
            block()
        }
}
