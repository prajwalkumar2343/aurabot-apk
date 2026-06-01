package com.aura.app.assistant

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface AuraApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/me")
    suspend fun me(): UserResponse

    @GET("memories")
    suspend fun memories(): List<MemoryResponse>

    @POST("memories")
    suspend fun createMemory(@Body request: MemoryCreateRequest): MemoryResponse

    @DELETE("memories/{id}")
    suspend fun deleteMemory(@Path("id") id: String)

    @GET("todos")
    suspend fun todos(): List<TodoResponse>

    @POST("todos")
    suspend fun createTodo(@Body request: TodoCreateRequest): TodoResponse

    @PATCH("todos/{id}")
    suspend fun updateTodo(@Path("id") id: String, @Body request: TodoUpdateRequest): TodoResponse

    @DELETE("todos/{id}")
    suspend fun deleteTodo(@Path("id") id: String)

    @POST("assistant/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse

    @POST("providers/openrouter/models")
    suspend fun openRouterModels(@Body request: OpenRouterModelsRequest): OpenRouterModelsResponse

    @POST("mini-apps/build")
    suspend fun buildMiniApp(@Body request: MiniAppBuildRequest): MiniAppBuildResponse

    @POST("transcribe")
    suspend fun transcribe(@Body request: TranscribeRequest): TranscribeResponse
}
