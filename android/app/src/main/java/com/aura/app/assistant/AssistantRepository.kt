package com.aura.app.assistant

import com.aura.app.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GuestFeatureException(feature: String) : IllegalStateException("$feature requires login")

class AssistantRepository(
    baseUrl: String,
    private val sessionStore: SessionStore
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

    suspend fun login(email: String, password: String): UserResponse = withContext(Dispatchers.IO) {
        val response = api.login(LoginRequest(email = email, password = password))
        sessionStore.setToken(response.access_token)
        UserResponse(response.id, response.email, response.name, response.role)
    }

    suspend fun me(): UserResponse = requireLogin("Account") { api.me() }

    suspend fun memories(): List<MemoryResponse> = requireLogin("Memories") { api.memories() }

    suspend fun createMemory(title: String, content: String): MemoryResponse =
        requireLogin("Memories") { api.createMemory(MemoryCreateRequest(title, content)) }

    suspend fun todos(): List<TodoResponse> = requireLogin("Tasks") { api.todos() }

    suspend fun createTodo(title: String): TodoResponse =
        requireLogin("Tasks") { api.createTodo(TodoCreateRequest(title)) }

    suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse =
        requireLogin("Tasks") { api.updateTodo(id, TodoUpdateRequest(done = done)) }

    suspend fun chat(message: String, sessionId: String?): ChatResponse =
        requireLogin("Assistant") { api.chat(ChatRequest(message = message, session_id = sessionId)) }

    private suspend fun <T> requireLogin(feature: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            if (sessionStore.accessToken().isNullOrBlank()) throw GuestFeatureException(feature)
            block()
        }
}
