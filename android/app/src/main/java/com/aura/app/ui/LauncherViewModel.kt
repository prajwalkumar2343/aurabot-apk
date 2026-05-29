package com.aura.app.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.app.AppContainer
import com.aura.app.apps.AppInfo
import com.aura.app.assistant.AssistantMessage
import com.aura.app.assistant.MemoryResponse
import com.aura.app.assistant.MessageRole
import com.aura.app.assistant.TodoResponse
import com.aura.app.session.SessionState
import com.aura.app.voice.ListeningStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val session: SessionState = SessionState(),
    val apps: List<AppInfo> = emptyList(),
    val appQuery: String = "",
    val assistantInput: String = "",
    val assistantSessionId: String? = null,
    val messages: List<AssistantMessage> = listOf(
        AssistantMessage(MessageRole.Assistant, "Aura is running locally. Ask about apps, tasks, or memories.")
    ),
    val memories: List<MemoryResponse> = emptyList(),
    val todos: List<TodoResponse> = emptyList(),
    val status: ListeningStatus = ListeningStatus(),
    val loading: Boolean = false,
    val error: String? = null
) {
    val filteredApps: List<AppInfo> =
        if (appQuery.isBlank()) apps else apps.filter {
            it.label.contains(appQuery, ignoreCase = true) ||
                it.packageName.contains(appQuery, ignoreCase = true)
        }

    val pinnedApps: List<AppInfo> = apps.take(5)
    val openTodos: Int = todos.count { !it.done }
}

class LauncherViewModel(private val container: AppContainer) : ViewModel() {
    private val localState = MutableStateFlow(LauncherUiState())

    val uiState: StateFlow<LauncherUiState> =
        combine(
            localState,
            container.sessionStore.state,
            container.voiceServiceController.status
        ) { state, session, voice ->
            state.copy(session = session, status = voice)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        refreshApps()
        refreshCloud()
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = container.appsRepository.loadLaunchableApps()
            localState.update { it.copy(apps = apps) }
        }
    }

    fun launchIntent(app: AppInfo): Intent = container.appsRepository.launchIntentFor(app)

    fun setAppQuery(query: String) {
        localState.update { it.copy(appQuery = query) }
    }

    fun setAssistantInput(input: String) {
        localState.update { it.copy(assistantInput = input) }
    }

    fun sendAssistantMessage() {
        val message = uiState.value.assistantInput.trim()
        if (message.isEmpty()) return
        viewModelScope.launch {
            localState.update {
                it.copy(
                    assistantInput = "",
                    loading = true,
                    error = null,
                    messages = it.messages + AssistantMessage(MessageRole.User, message)
                )
            }
            try {
                val response = container.assistantRepository.chat(message, uiState.value.assistantSessionId)
                localState.update {
                    it.copy(
                        loading = false,
                        assistantSessionId = response.session_id,
                        messages = it.messages + AssistantMessage(MessageRole.Assistant, response.reply)
                    )
                }
            } catch (error: Exception) {
                localState.update { it.copy(loading = false, error = error.message ?: "Assistant failed") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.sessionStore.setToken(null)
            localState.update {
                it.copy(
                    memories = emptyList(),
                    todos = emptyList(),
                    assistantSessionId = null,
                    messages = listOf(AssistantMessage(MessageRole.Assistant, "Aura is back in local mode."))
                )
            }
        }
    }

    fun refreshCloud() {
        viewModelScope.launch {
            try {
                val memories = container.assistantRepository.memories()
                val todos = container.assistantRepository.todos()
                localState.update { it.copy(memories = memories, todos = todos, error = null) }
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message) }
            }
        }
    }

    fun addTodo(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                container.assistantRepository.createTodo(trimmed)
                refreshCloud()
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message ?: "Could not add task") }
            }
        }
    }

    fun addMemory(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            try {
                container.assistantRepository.createMemory(title.trim(), content.trim())
                refreshCloud()
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message ?: "Could not add memory") }
            }
        }
    }

    fun setBackgroundListening(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !container.voiceServiceController.hasRequiredPermissions()) {
                localState.update { it.copy(error = "Microphone permission is required") }
                return@launch
            }
            container.voiceServiceController.setBackgroundListening(enabled)
        }
    }

    fun startPushToTalk(): Boolean = container.voiceServiceController.start()

    fun stopVoice() {
        container.voiceServiceController.stop()
    }

    fun clearError() {
        localState.update { it.copy(error = null) }
    }

    fun markHomeSettingsPrompted() {
        viewModelScope.launch {
            container.sessionStore.setHomeSettingsPrompted(true)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(container) as T
        }
    }
}
