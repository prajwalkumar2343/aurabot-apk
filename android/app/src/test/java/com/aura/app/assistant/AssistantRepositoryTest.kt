package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import com.aura.app.apps.AppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRepositoryTest {
    @Test
    fun durableRunPollingBacksOffAfterFastInitialUpdates() {
        assertEquals(250L, AssistantRepository.agentRunPollDelayMillis(0))
        assertEquals(500L, AssistantRepository.agentRunPollDelayMillis(8))
        assertEquals(1_000L, AssistantRepository.agentRunPollDelayMillis(20))
        assertEquals(2_000L, AssistantRepository.agentRunPollDelayMillis(50))
    }

    @Test
    fun registerStoresTokensAndAllowsAuthenticatedMeWithoutLogin() = runTest {
        val store = FakeRepositoryTokenStore()
        val api = FakeAuraApi(store)
        val auth = FakeRepositoryAuthTransport()
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore(),
            supabaseAuth = auth
        )

        val registered = repository.register("new@aura.app", "password123", "New User")
        val me = repository.me()

        assertEquals(UserResponse("user-1", "new@aura.app", "New User", "authenticated", "managed"), registered)
        assertEquals(UserResponse("user-1", "", null, "authenticated", "managed"), me)
        assertEquals("access-from-register", store.accessTokenValue)
        assertEquals("refresh-from-register", store.refreshTokenValue)
        assertEquals(0, api.legacyAuthCalls)
        assertEquals(1, api.meCalls)
    }

    @Test
    fun chatUsesTheCurrentlyVisibleMemoryAndTaskContext() = runTest {
        val store = FakeRepositoryTokenStore().apply {
            accessTokenValue = "access-token"
            serviceModeValue = "managed"
        }
        val api = FakeAuraApi(store)
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore(
                LlmSettingsState(googleApiKey = "api-key")
            )
        )
        val memories = listOf(MemoryResponse("memory-1", "Home", "Blue door", "now"))
        val todos = listOf(TodoResponse("todo-1", "Buy milk", false, "now"))

        repository.chat(
            message = "What should I remember?",
            sessionId = "session-1",
            memories = memories,
            todos = todos,
            apps = emptyList<AppInfo>()
        )

        assertEquals(
            listOf(ChatMemoryItem("Home", "Blue door")),
            api.lastChatRequest?.memories
        )
        assertEquals(
            listOf(ChatTodoItem("Buy milk", false)),
            api.lastChatRequest?.todos
        )
    }

    @Test
    fun chatPollsDurableRunAndReportsSubagentProgress() = runTest {
        val store = FakeRepositoryTokenStore().apply {
            accessTokenValue = "access-token"
            serviceModeValue = "managed"
        }
        val api = FakeAuraApi(store).apply {
            runResponses += AgentRunResponse(
                id = "run-1",
                session_id = "session-1",
                state = "running",
                phase = "delegating",
                children = listOf(
                    AgentChildRun("child-1", "planner", "running", "planning")
                )
            )
            runResponses += AgentRunResponse(
                id = "run-1",
                session_id = "session-1",
                state = "completed",
                phase = "completed",
                reply = "Finished",
                emotion = "encouraging",
                created_emotion = "create dreamily curious"
            )
        }
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore(
                LlmSettingsState(googleApiKey = "api-key")
            )
        )
        val progress = mutableListOf<AssistantRunProgress>()

        val response = repository.chat(
            message = "Plan this",
            sessionId = "session-1",
            memories = emptyList(),
            todos = emptyList(),
            apps = emptyList(),
            onRunProgress = progress::add
        )

        assertEquals("Finished", response.reply)
        assertEquals("encouraging", response.emotion)
        assertEquals("create dreamily curious", response.created_emotion)
        assertEquals(listOf("admitted", "delegating", "completed"), progress.map { it.phase })
        assertEquals(1, progress[1].activeSubagents)
        assertEquals(2, api.assistantRunCalls)
    }

    @Test
    fun localChatEmitsLocalProgressWithoutStartingAManagedRun() = runTest {
        val store = FakeRepositoryTokenStore().apply {
            serviceModeValue = "local"
        }
        val api = FakeAuraApi(store)
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore(
                LlmSettingsState(googleApiKey = "local-provider-key")
            ),
            localProviderGateway = FakeLocalProviderGateway()
        )
        val progress = mutableListOf<AssistantRunProgress>()

        val response = repository.chat(
            message = "Keep this local",
            sessionId = "local-session",
            memories = emptyList(),
            todos = emptyList(),
            apps = emptyList(),
            onRunProgress = progress::add
        )

        assertEquals("{neutral} Local result", response.reply)
        assertEquals(listOf("admitted", "planning", "completed"), progress.map { it.phase })
        assertTrue(progress.all { it.mode == AssistantRunMode.Local })
        assertEquals(0, api.assistantRunCalls)
    }

    @Test
    fun managedGoogleSessionUsesAuraCredentialsWithoutReadingALocalKey() = runTest {
        val store = FakeRepositoryTokenStore().apply {
            accessTokenValue = "managed-access"
            serviceModeValue = "managed"
        }
        val api = FakeAuraApi(store)
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore(
                LlmSettingsState(provider = LlmProvider.OpenAI)
            )
        )

        repository.chat(
            message = "Work out of the box",
            sessionId = "managed-session",
            memories = emptyList(),
            todos = emptyList(),
            apps = emptyList()
        )

        assertEquals("", api.lastChatRequest?.api_key)
        assertEquals("gemini", api.lastChatRequest?.provider)
        assertEquals(DEFAULT_GEMINI_MODEL, api.lastChatRequest?.model)
    }
}

private class FakeAuraApi(
    private val store: FakeRepositoryTokenStore
) : AuraApi {
    var legacyAuthCalls = 0
    var meCalls = 0
    var assistantRunCalls = 0
    var lastChatRequest: ChatRequest? = null
    val runResponses = mutableListOf<AgentRunResponse>()

    override suspend fun me(): StalkyPrincipalResponse {
        meCalls += 1
        assertEquals("access-from-register", store.accessTokenValue)
        return StalkyPrincipalResponse("user-1", "authenticated")
    }

    override suspend fun memories(): List<MemoryResponse> = unsupported()
    override suspend fun createMemory(request: MemoryCreateRequest): MemoryResponse = unsupported()
    override suspend fun deleteMemory(id: String) = unsupported()
    override suspend fun todos(): List<TodoResponse> = unsupported()
    override suspend fun createTodo(request: TodoCreateRequest): TodoResponse = unsupported()
    override suspend fun updateTodo(id: String, request: TodoUpdateRequest): TodoResponse = unsupported()
    override suspend fun deleteTodo(id: String) = unsupported()
    override suspend fun chat(request: ChatRequest): ChatResponse {
        lastChatRequest = request
        return ChatResponse("{neutral} Ready", request.session_id.orEmpty())
    }
    override suspend fun startAssistantRun(idempotencyKey: String, request: ChatRequest): AgentRunAccepted {
        require(idempotencyKey.isNotBlank())
        lastChatRequest = request
        return AgentRunAccepted("run-1", request.session_id.orEmpty(), "queued")
    }
    override suspend fun assistantRun(runId: String): AgentRunResponse {
        assistantRunCalls += 1
        return if (runResponses.isNotEmpty()) {
            runResponses.removeAt(0)
        } else {
            AgentRunResponse(
                id = runId,
                session_id = lastChatRequest?.session_id.orEmpty(),
                state = "completed",
                phase = "completed",
                reply = "{neutral} Ready"
            )
        }
    }
    override suspend fun openRouterModels(request: OpenRouterModelsRequest): OpenRouterModelsResponse = unsupported()
    override suspend fun buildMiniApp(request: MiniAppBuildRequest): MiniAppBuildResponse = unsupported()
    override suspend fun reviseMiniApp(request: MiniAppRevisionRequest): MiniAppRevisionResponse = unsupported()
    override suspend fun transcribe(request: TranscribeRequest): TranscribeResponse = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used in this test")
}

private class FakeRepositoryAuthTransport : SupabaseAuthTransport {
    private val session = SupabaseAuthSession(
        access_token = "access-from-register",
        refresh_token = "refresh-from-register",
        user = SupabaseAuthUser(
            id = "user-1",
            email = "new@aura.app",
            userMetadata = SupabaseUserMetadata(fullName = "New User")
        )
    )

    override suspend fun exchangeGoogleIdToken(idToken: String, nonce: String): SupabaseAuthSession = session
    override suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSession = session
    override suspend fun signUp(email: String, password: String, name: String?): SupabaseAuthSession {
        assertEquals("new@aura.app", email)
        assertEquals("password123", password)
        assertEquals("New User", name)
        return session
    }
    override suspend fun refresh(refreshToken: String): SupabaseAuthSession = session
    override suspend fun logout(accessToken: String) = Unit
}

private class FakeRepositoryTokenStore : AuthTokenStore {
    var accessTokenValue: String? = null
    var refreshTokenValue: String? = null
    var serviceModeValue: String = "local"

    override suspend fun accessToken(): String? = accessTokenValue

    override suspend fun refreshToken(): String? = refreshTokenValue

    override suspend fun setTokens(accessToken: String?, refreshToken: String?) {
        accessTokenValue = accessToken
        refreshTokenValue = refreshToken
    }

    override suspend fun clearTokens() {
        accessTokenValue = null
        refreshTokenValue = null
    }

    override suspend fun serviceMode(): String = serviceModeValue

    override suspend fun setServiceMode(mode: String) {
        serviceModeValue = mode
    }
}

private class FakeAssistantLocalStore : AssistantLocalStore {
    override suspend fun memories(): List<MemoryResponse> = emptyList()
    override suspend fun createMemory(title: String, content: String): MemoryResponse = unsupported()
    override suspend fun todos(): List<TodoResponse> = emptyList()
    override suspend fun createTodo(title: String): TodoResponse = unsupported()
    override suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used in this test")
}

private class FakeAssistantLlmSettingsStore(
    initialState: LlmSettingsState = LlmSettingsState()
) : AssistantLlmSettingsStore {
    override val state: Flow<LlmSettingsState> = MutableStateFlow(initialState)
    override suspend fun setProvider(provider: LlmProvider) = Unit
    override suspend fun setGoogleApiKey(value: String) = Unit
    override suspend fun setGoogleModel(value: String) = Unit
    override suspend fun setOpenAiApiKey(value: String) = Unit
    override suspend fun setOpenAiModel(value: String) = Unit
    override suspend fun setOpenRouterApiKey(value: String) = Unit
    override suspend fun setOpenRouterModel(value: String) = Unit
}

private class FakeLocalProviderGateway : LocalProviderGateway {
    override suspend fun chat(request: ChatRequest, settings: LlmSettingsState) =
        ChatResponse("{neutral} Local result", request.session_id.orEmpty())

    override suspend fun transcribe(audioBase64: String, mimeType: String, settings: LlmSettingsState) =
        "local transcript"

    override suspend fun openRouterModels(apiKey: String) = emptyList<OpenRouterModelInfo>()
}
