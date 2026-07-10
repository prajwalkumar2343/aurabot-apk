package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRepositoryTest {
    @Test
    fun registerStoresTokensAndAllowsAuthenticatedMeWithoutLogin() = runTest {
        val store = FakeRepositoryTokenStore()
        val api = FakeAuraApi(store)
        val repository = AssistantRepository(
            api = api,
            sessionStore = store,
            localAssistantStore = FakeAssistantLocalStore(),
            llmSettingsStore = FakeAssistantLlmSettingsStore()
        )

        val registered = repository.register("new@aura.app", "password123", "New User")
        val me = repository.me()

        assertEquals(UserResponse("user-1", "new@aura.app", "New User", "user"), registered)
        assertEquals(UserResponse("user-1", "new@aura.app", "New User", "user"), me)
        assertEquals("access-from-register", store.accessTokenValue)
        assertEquals("refresh-from-register", store.refreshTokenValue)
        assertEquals(1, api.registerCalls)
        assertEquals(0, api.loginCalls)
        assertEquals(1, api.meCalls)
    }
}

private class FakeAuraApi(
    private val store: FakeRepositoryTokenStore
) : AuraApi {
    var registerCalls = 0
    var loginCalls = 0
    var meCalls = 0

    override suspend fun register(request: RegisterRequest): LoginResponse {
        registerCalls += 1
        assertEquals(RegisterRequest("new@aura.app", "password123", "New User"), request)
        return LoginResponse(
            id = "user-1",
            email = request.email,
            name = request.name,
            role = "user",
            access_token = "access-from-register",
            refresh_token = "refresh-from-register"
        )
    }

    override suspend fun login(request: LoginRequest): LoginResponse {
        loginCalls += 1
        throw AssertionError("register() must not make a follow-up login request")
    }

    override suspend fun me(): UserResponse {
        meCalls += 1
        assertEquals("access-from-register", store.accessTokenValue)
        return UserResponse("user-1", "new@aura.app", "New User", "user")
    }

    override suspend fun logout(request: RefreshRequest) = unsupported()
    override suspend fun refresh(request: RefreshRequest): RefreshResponse = unsupported()
    override suspend fun memories(): List<MemoryResponse> = unsupported()
    override suspend fun createMemory(request: MemoryCreateRequest): MemoryResponse = unsupported()
    override suspend fun deleteMemory(id: String) = unsupported()
    override suspend fun todos(): List<TodoResponse> = unsupported()
    override suspend fun createTodo(request: TodoCreateRequest): TodoResponse = unsupported()
    override suspend fun updateTodo(id: String, request: TodoUpdateRequest): TodoResponse = unsupported()
    override suspend fun deleteTodo(id: String) = unsupported()
    override suspend fun chat(request: ChatRequest): ChatResponse = unsupported()
    override suspend fun openRouterModels(request: OpenRouterModelsRequest): OpenRouterModelsResponse = unsupported()
    override suspend fun buildMiniApp(request: MiniAppBuildRequest): MiniAppBuildResponse = unsupported()
    override suspend fun reviseMiniApp(request: MiniAppRevisionRequest): MiniAppRevisionResponse = unsupported()
    override suspend fun transcribe(request: TranscribeRequest): TranscribeResponse = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used in this test")
}

private class FakeRepositoryTokenStore : AuthTokenStore {
    var accessTokenValue: String? = null
    var refreshTokenValue: String? = null

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
}

private class FakeAssistantLocalStore : AssistantLocalStore {
    override suspend fun memories(): List<MemoryResponse> = emptyList()
    override suspend fun createMemory(title: String, content: String): MemoryResponse = unsupported()
    override suspend fun todos(): List<TodoResponse> = emptyList()
    override suspend fun createTodo(title: String): TodoResponse = unsupported()
    override suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Not used in this test")
}

private class FakeAssistantLlmSettingsStore : AssistantLlmSettingsStore {
    override val state: Flow<LlmSettingsState> = MutableStateFlow(LlmSettingsState())
    override suspend fun setProvider(provider: LlmProvider) = Unit
    override suspend fun setGoogleApiKey(value: String) = Unit
    override suspend fun setGoogleModel(value: String) = Unit
    override suspend fun setOpenAiApiKey(value: String) = Unit
    override suspend fun setOpenAiModel(value: String) = Unit
    override suspend fun setOpenRouterApiKey(value: String) = Unit
    override suspend fun setOpenRouterModel(value: String) = Unit
}
