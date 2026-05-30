package com.aura.app.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aura.app.AppContainer
import com.aura.app.apps.AppBlockRule
import com.aura.app.apps.AppInfo
import com.aura.app.assistant.AssistantMessage
import com.aura.app.assistant.ChatAction
import com.aura.app.assistant.LlmProvider
import com.aura.app.assistant.LlmSettingsState
import com.aura.app.assistant.MemoryResponse
import com.aura.app.assistant.MessageRole
import com.aura.app.assistant.OpenRouterModelInfo
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
    val appBlocks: List<AppBlockRule> = emptyList(),
    val llmSettings: LlmSettingsState = LlmSettingsState(),
    val openRouterModels: List<OpenRouterModelInfo> = emptyList(),
    val loadingModels: Boolean = false,
    val status: ListeningStatus = ListeningStatus(),
    val loading: Boolean = false,
    val error: String? = null,
    val currentEmotion: String = "neutral",
    val isSpeaking: Boolean = false
) {
    val filteredApps: List<AppInfo> =
        if (appQuery.isBlank()) apps else apps.filter {
            it.label.contains(appQuery, ignoreCase = true) ||
                it.packageName.contains(appQuery, ignoreCase = true)
        }

    val pinnedApps: List<AppInfo> = apps.take(5)
    val openTodos: Int = todos.count { !it.done }
    val recentMessages: List<AssistantMessage> = messages.takeLast(4)
}

class LauncherViewModel(private val container: AppContainer) : ViewModel() {
    private val localState = MutableStateFlow(LauncherUiState())

    private var tts: TextToSpeech? = null
    private val emotionSegmentsList = mutableListOf<EmotionSegment>()

    data class EmotionSegment(val emotion: String, val text: String, val id: String)

    val uiState: StateFlow<LauncherUiState> =
        combine(
            localState,
            container.sessionStore.state,
            container.voiceServiceController.status,
            container.llmSettingsStore.state,
            container.appBlockStore.activeRules
        ) { state, session, voice, llmSettings, appBlocks ->
            state.copy(session = session, status = voice, llmSettings = llmSettings, appBlocks = appBlocks)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        refreshApps()
        refreshCloud()
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(container.appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                setupTtsListener()
            }
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                val segment = emotionSegmentsList.firstOrNull { it.id == utteranceId }
                if (segment != null) {
                    localState.update { it.copy(currentEmotion = segment.emotion, isSpeaking = true) }
                }
            }

            override fun onDone(utteranceId: String) {
                val lastId = emotionSegmentsList.lastOrNull()?.id
                if (lastId == utteranceId) {
                    localState.update { it.copy(currentEmotion = "neutral", isSpeaking = false) }
                }
            }

            override fun onError(utteranceId: String) {
                localState.update { it.copy(currentEmotion = "neutral", isSpeaking = false) }
            }
        })
    }

    private fun parseEmotionSegments(reply: String): List<EmotionSegment> {
        val segments = mutableListOf<EmotionSegment>()
        val regex = """\{([a-zA-Z0-9_-]+)\}""".toRegex()
        
        var lastIndex = 0
        var currentEmotion = "neutral"
        
        val matches = regex.findAll(reply).toList()
        if (matches.isEmpty()) {
            if (reply.isNotBlank()) {
                segments.add(EmotionSegment("neutral", reply, "seg_0"))
            }
            return segments
        }
        
        var segId = 0
        for (match in matches) {
            val range = match.range
            val textBefore = reply.substring(lastIndex, range.first).trim()
            if (textBefore.isNotEmpty()) {
                segments.add(EmotionSegment(currentEmotion, textBefore, "seg_${segId++}"))
            }
            currentEmotion = match.groupValues[1].lowercase()
            lastIndex = range.last + 1
        }
        
        if (lastIndex < reply.length) {
            val textAfter = reply.substring(lastIndex).trim()
            if (textAfter.isNotEmpty()) {
                segments.add(EmotionSegment(currentEmotion, textAfter, "seg_${segId++}"))
            }
        }
        
        return segments
    }

    private fun speakReply(reply: String) {
        tts?.stop()
        emotionSegmentsList.clear()
        
        val parsed = parseEmotionSegments(reply)
        if (parsed.isEmpty()) return
        
        emotionSegmentsList.addAll(parsed)
        parsed.forEach { segment ->
            tts?.speak(segment.text, TextToSpeech.QUEUE_ADD, null, segment.id)
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = container.appsRepository.loadLaunchableApps()
            localState.update { it.copy(apps = apps) }
        }
    }

    fun launchIntent(app: AppInfo): Intent? {
        val block = uiState.value.appBlocks.firstOrNull { it.packageName == app.packageName && it.isActive() }
        if (block != null) {
            localState.update {
                it.copy(error = "${app.label} is blocked for ${block.remainingMinutes()} more minutes")
            }
            return null
        }
        return container.appsRepository.launchIntentFor(app)
    }

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
                val response = container.assistantRepository.chat(
                    message = message,
                    sessionId = uiState.value.assistantSessionId,
                    apps = uiState.value.apps
                )
                val actionReplies = applyChatActions(response.actions)
                localState.update {
                    it.copy(
                        loading = false,
                        assistantSessionId = response.session_id,
                        messages = it.messages +
                            AssistantMessage(MessageRole.Assistant, response.reply) +
                            actionReplies.map { text -> AssistantMessage(MessageRole.Assistant, text) }
                    )
                }
                speakReply(response.reply)
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

    private suspend fun applyChatActions(actions: List<ChatAction>): List<String> {
        val replies = mutableListOf<String>()
        actions.forEach { action ->
            when (action.type) {
                "block_app" -> {
                    val durationMinutes = action.duration_minutes ?: 0
                    val app = resolveApp(action.package_name, action.app_query)
                    if (app == null) {
                        replies += "I could not find the app to block."
                    } else if (durationMinutes <= 0) {
                        replies += "I need a positive duration to block ${app.label}."
                    } else {
                        val rule = container.appBlockStore.blockApp(app, durationMinutes)
                        replies += "${rule.label} is blocked for ${rule.remainingMinutes()} minutes."
                    }
                }
            }
        }
        return replies
    }

    private fun resolveApp(packageName: String?, appQuery: String?): AppInfo? {
        val apps = uiState.value.apps
        packageName?.takeIf { it.isNotBlank() }?.let { packageId ->
            apps.firstOrNull { it.packageName == packageId }?.let { return it }
        }
        val query = appQuery?.trim()?.lowercase().orEmpty()
        if (query.isBlank()) return null
        return apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.equals(query, ignoreCase = true) }
            ?: apps.firstOrNull {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
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

    fun setLlmProvider(provider: LlmProvider) {
        viewModelScope.launch {
            container.assistantRepository.setProvider(provider)
        }
    }

    fun setGoogleApiKey(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setGoogleApiKey(value)
        }
    }

    fun setGoogleModel(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setGoogleModel(value)
        }
    }

    fun setOpenAiApiKey(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setOpenAiApiKey(value)
        }
    }

    fun setOpenAiModel(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setOpenAiModel(value)
        }
    }

    fun setOpenRouterApiKey(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setOpenRouterApiKey(value)
        }
    }

    fun setOpenRouterModel(value: String) {
        viewModelScope.launch {
            container.assistantRepository.setOpenRouterModel(value)
        }
    }

    fun loadOpenRouterModels() {
        viewModelScope.launch {
            localState.update { it.copy(loadingModels = true, error = null) }
            try {
                val models = container.assistantRepository.openRouterModels()
                localState.update {
                    it.copy(
                        loadingModels = false,
                        openRouterModels = models
                    )
                }
            } catch (error: Exception) {
                localState.update {
                    it.copy(
                        loadingModels = false,
                        error = error.message ?: "Could not load OpenRouter models"
                    )
                }
            }
        }
    }

    fun markHomeSettingsPrompted() {
        viewModelScope.launch {
            container.sessionStore.setHomeSettingsPrompted(true)
        }
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(container) as T
        }
    }
}
