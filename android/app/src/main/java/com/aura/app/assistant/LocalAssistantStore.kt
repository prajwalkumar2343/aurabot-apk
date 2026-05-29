package com.aura.app.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

private val Context.localAssistantDataStore by preferencesDataStore(name = "aura_local_assistant")

private data class LocalAssistantState(
    val memories: List<MemoryResponse> = emptyList(),
    val todos: List<TodoResponse> = emptyList()
)

class LocalAssistantStore(private val context: Context) {
    private val gson = Gson()
    private val stateKey = stringPreferencesKey("local_state")

    suspend fun memories(): List<MemoryResponse> = readState().memories

    suspend fun createMemory(title: String, content: String): MemoryResponse {
        val memory = MemoryResponse(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content,
            created_at = Instant.now().toString()
        )
        updateState { state -> state.copy(memories = listOf(memory) + state.memories) }
        return memory
    }

    suspend fun todos(): List<TodoResponse> = readState().todos

    suspend fun createTodo(title: String): TodoResponse {
        val todo = TodoResponse(
            id = UUID.randomUUID().toString(),
            title = title,
            done = false,
            created_at = Instant.now().toString()
        )
        updateState { state -> state.copy(todos = listOf(todo) + state.todos) }
        return todo
    }

    suspend fun updateTodoDone(id: String, done: Boolean): TodoResponse {
        var updated: TodoResponse? = null
        updateState { state ->
            val todos = state.todos.map { todo ->
                if (todo.id == id) {
                    todo.copy(done = done).also { updated = it }
                } else {
                    todo
                }
            }
            state.copy(todos = todos)
        }
        return requireNotNull(updated) { "Todo not found" }
    }

    private suspend fun readState(): LocalAssistantState {
        val raw = context.localAssistantDataStore.data.first()[stateKey] ?: return LocalAssistantState()
        return gson.fromJson(raw, localStateType) ?: LocalAssistantState()
    }

    private suspend fun updateState(transform: (LocalAssistantState) -> LocalAssistantState) {
        context.localAssistantDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { gson.fromJson<LocalAssistantState>(it, localStateType) }
                ?: LocalAssistantState()
            prefs[stateKey] = gson.toJson(transform(current), localStateType)
        }
    }

    private companion object {
        val localStateType = object : TypeToken<LocalAssistantState>() {}.type
    }
}
