package com.aura.app.assistant

import com.aura.app.session.AuthTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRepositoryCloudAuthTest {
    @Test
    fun googleIdTokenExchangeStoresManagedSupabaseSession() = runTest {
        val store = FakeCloudTokenStore()
        val auth = FakeCloudAuthTransport(
            session = SupabaseAuthSession(
                access_token = "supabase-access",
                refresh_token = "supabase-refresh",
                user = SupabaseAuthUser(
                    id = "user-1",
                    email = "user@example.com",
                    userMetadata = SupabaseUserMetadata(fullName = "User")
                )
            )
        )
        val repository = repository(store, auth)

        val nonce = repository.googleSignInChallenge()
        val user = repository.loginWithGoogle("google-id-token", nonce)

        assertTrue(nonce.isNotBlank())
        assertEquals(43, nonce.length)
        assertEquals("google-id-token", auth.idToken)
        assertEquals(nonce, auth.nonce)
        assertEquals(UserResponse("user-1", "user@example.com", "User", "authenticated", "managed"), user)
        assertEquals("supabase-access", store.accessTokenValue)
        assertEquals("supabase-refresh", store.refreshTokenValue)
        assertEquals("managed", store.serviceModeValue)
    }

    @Test
    fun logoutRevokesManagedSessionWhenConfiguredAndAlwaysClearsLocalTokens() = runTest {
        val store = FakeCloudTokenStore().apply {
            accessTokenValue = "access"
            refreshTokenValue = "refresh"
            serviceModeValue = "managed"
        }
        val auth = FakeCloudAuthTransport()
        val repository = repository(store, auth)

        repository.logout()

        assertEquals(listOf("access"), auth.logoutTokens)
        assertEquals(null, store.accessTokenValue)
        assertEquals(null, store.refreshTokenValue)
        assertEquals("local", store.serviceModeValue)
    }

    @Test
    fun missingManagedConfigurationFailsClosedWithoutCallingAProvider() = runTest {
        val store = FakeCloudTokenStore().apply { serviceModeValue = "managed" }
        val provider = CloudBoundaryFakeLocalProviderGateway()
        val repository = AssistantRepository(
            api = null,
            sessionStore = store,
            localAssistantStore = FakeLocalStore(),
            llmSettingsStore = FakeLlmSettingsStore(),
            localProviderGateway = provider
        )

        val error = runCatching { repository.memories() }.exceptionOrNull()

        assertTrue(error is StalkyCloudUnavailableException)
        assertEquals(0, provider.calls)
    }

    @Test
    fun localModeUsesDeviceStoreWithoutCloudConfiguration() = runTest {
        val store = FakeCloudTokenStore()
        val repository = repository(store, auth = FakeCloudAuthTransport())

        assertEquals(emptyList<MemoryResponse>(), repository.memories())
        assertEquals(emptyList<TodoResponse>(), repository.todos())
    }

    private fun repository(
        store: FakeCloudTokenStore,
        auth: FakeCloudAuthTransport
    ) = AssistantRepository(
        api = null,
        sessionStore = store,
        localAssistantStore = FakeLocalStore(),
        llmSettingsStore = FakeLlmSettingsStore(),
        supabaseAuth = auth
    )
}

private class FakeCloudAuthTransport(
    private val session: SupabaseAuthSession? = null
) : SupabaseAuthTransport {
    var idToken: String? = null
    var nonce: String? = null
    val logoutTokens = mutableListOf<String>()

    override suspend fun exchangeGoogleIdToken(
        idToken: String,
        nonce: String
    ): SupabaseAuthSession {
        this.idToken = idToken
        this.nonce = nonce
        return requireNotNull(session)
    }

    override suspend fun signInWithPassword(email: String, password: String): SupabaseAuthSession =
        requireNotNull(session)

    override suspend fun signUp(email: String, password: String, name: String?): SupabaseAuthSession =
        requireNotNull(session)

    override suspend fun refresh(refreshToken: String): SupabaseAuthSession = requireNotNull(session)

    override suspend fun logout(accessToken: String) {
        logoutTokens += accessToken
    }
}

private class FakeCloudTokenStore : AuthTokenStore {
    var accessTokenValue: String? = null
    var refreshTokenValue: String? = null
    var serviceModeValue = "local"

    override suspend fun accessToken(): String? = accessTokenValue
    override suspend fun refreshToken(): String? = refreshTokenValue
    override suspend fun setTokens(accessToken: String?, refreshToken: String?) {
        accessTokenValue = accessToken
        refreshTokenValue = refreshToken
    }
    override suspend fun clearTokens() {
        accessTokenValue = null
        refreshTokenValue = null
        serviceModeValue = "local"
    }
    override suspend fun serviceMode(): String = serviceModeValue
    override suspend fun setServiceMode(mode: String) {
        serviceModeValue = mode
    }
}

private class FakeLocalStore : AssistantLocalStore {
    override suspend fun memories(): List<MemoryResponse> = emptyList()
    override suspend fun createMemory(title: String, content: String): MemoryResponse = error("unused")
    override suspend fun todos(): List<TodoResponse> = emptyList()
    override suspend fun createTodo(title: String): TodoResponse = error("unused")
    override suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = error("unused")
}

private class FakeLlmSettingsStore : AssistantLlmSettingsStore {
    override val state: Flow<LlmSettingsState> = MutableStateFlow(LlmSettingsState())
    override suspend fun setProvider(provider: LlmProvider) = Unit
    override suspend fun setGoogleApiKey(value: String) = Unit
    override suspend fun setGoogleModel(value: String) = Unit
    override suspend fun setOpenAiApiKey(value: String) = Unit
    override suspend fun setOpenAiModel(value: String) = Unit
    override suspend fun setOpenRouterApiKey(value: String) = Unit
    override suspend fun setOpenRouterModel(value: String) = Unit
}

private class CloudBoundaryFakeLocalProviderGateway : LocalProviderGateway {
    var calls = 0
    override suspend fun chat(request: ChatRequest, settings: LlmSettingsState): ChatResponse {
        calls += 1
        error("unused")
    }
    override suspend fun transcribe(audioBase64: String, mimeType: String, settings: LlmSettingsState): String {
        calls += 1
        error("unused")
    }
    override suspend fun openRouterModels(apiKey: String): List<OpenRouterModelInfo> {
        calls += 1
        error("unused")
    }
}
