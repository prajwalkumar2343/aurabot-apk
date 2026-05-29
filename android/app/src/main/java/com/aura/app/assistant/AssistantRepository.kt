package com.aura.app.assistant

import com.aura.app.session.SessionStore
import kotlinx.coroutines.Dispatchers
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
    private val localAssistantStore: LocalAssistantStore
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

    suspend fun memories(): List<MemoryResponse> = localAssistantStore.memories()

    suspend fun createMemory(title: String, content: String): MemoryResponse = localAssistantStore.createMemory(title, content)

    suspend fun todos(): List<TodoResponse> = localAssistantStore.todos()

    suspend fun createTodo(title: String): TodoResponse = localAssistantStore.createTodo(title)

    suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse = localAssistantStore.updateTodoDone(id, done)

    suspend fun chat(message: String, sessionId: String?): ChatResponse = withContext(Dispatchers.IO) {
        val todos = localAssistantStore.todos()
        val memories = localAssistantStore.memories()
        val reply = buildString {
            append("Working locally. ")
            when {
                "task" in message.lowercase() || "todo" in message.lowercase() ->
                    append("You have ${todos.count { !it.done }} open tasks on this device.")
                "memory" in message.lowercase() || "remember" in message.lowercase() ->
                    append("I can see ${memories.size} saved memories locally.")
                else ->
                    append("Ask me about apps, tasks, memories, or use push-to-talk.")
            }
        }
        ChatResponse(reply = reply, session_id = sessionId ?: UUID.randomUUID().toString())
    }

    private suspend fun <T> requireLogin(feature: String, block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            if (sessionStore.accessToken().isNullOrBlank()) throw GuestFeatureException(feature)
            block()
        }
}
