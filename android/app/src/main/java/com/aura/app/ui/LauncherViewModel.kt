package com.aura.app.ui

import android.content.Intent
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
import com.aura.app.assistant.UserResponse
import com.aura.app.miniapps.BuiltInMiniApps
import com.aura.app.miniapps.MiniAppBundle
import com.aura.app.miniapps.MiniAppInstall
import com.aura.app.miniapps.MiniAppRecord
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
    val miniApps: List<MiniAppInstall> = emptyList(),
    val builtInMiniApps: List<MiniAppBundle> = BuiltInMiniApps.all,
    val activeMiniApp: MiniAppBundle? = null,
    val activeMiniAppRecords: List<MiniAppRecord> = emptyList(),
    val llmSettings: LlmSettingsState = LlmSettingsState(),
    val openRouterModels: List<OpenRouterModelInfo> = emptyList(),
    val loadingModels: Boolean = false,
    val status: ListeningStatus = ListeningStatus(),
    val loading: Boolean = false,
    val error: String? = null,
    val currentEmotion: String = "neutral",
    val isSpeaking: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val sessionLoaded: Boolean = false
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

    val uiState: StateFlow<LauncherUiState> =
        combine(
            localState,
            container.sessionStore.state,
            container.voiceServiceController.status,
            container.llmSettingsStore.state,
            container.appBlockStore.activeRules
        ) { state, session, voice, llmSettings, appBlocks ->
            state.copy(
                session = session,
                status = voice,
                llmSettings = llmSettings,
                appBlocks = appBlocks,
                sessionLoaded = true
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    init {
        refreshApps(force = true)
        refreshMiniApps()
        refreshCloud()
    }

    private fun updateEmotionFromReply(reply: String) {
        val regex = """\{([a-zA-Z0-9_-]+)\}""".toRegex()
        val match = regex.find(reply)
        val emotion = match?.groupValues?.get(1)?.lowercase() ?: "neutral"
        localState.update { it.copy(currentEmotion = emotion, isSpeaking = false) }
    }

    fun refreshApps(force: Boolean = false) {
        viewModelScope.launch {
            val apps = container.appsRepository.loadLaunchableApps(force).toMutableList()
            if (uiState.value.isDefaultLauncher) {
                apps.add(
                    AppInfo(
                        label = "Aurabot Settings",
                        packageName = "com.aura.app.settings",
                        componentName = android.content.ComponentName("com.aura.app", "com.aura.app.SettingsActivity"),
                        icon = null
                    )
                )
            }
            apps.sortBy { it.label.lowercase() }
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
                val bundles = uiState.value.miniApps.mapNotNull { container.miniAppRepository.bundle(it.id) }
                val response = container.assistantRepository.chat(
                    message = message,
                    sessionId = uiState.value.assistantSessionId,
                    apps = uiState.value.apps,
                    miniApps = uiState.value.miniApps,
                    miniAppBundles = bundles
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
                updateEmotionFromReply(response.reply)
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

    fun register(email: String, password: String, name: String?, onResult: (Result<UserResponse>) -> Unit) {
        viewModelScope.launch {
            localState.update { it.copy(loading = true, error = null) }
            val result = runCatching { container.assistantRepository.register(email, password, name) }
            localState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
            onResult(result)
        }
    }

    fun login(email: String, password: String, onResult: (Result<UserResponse>) -> Unit) {
        viewModelScope.launch {
            localState.update { it.copy(loading = true, error = null) }
            val result = runCatching { container.assistantRepository.login(email, password) }
            localState.update { it.copy(loading = false, error = result.exceptionOrNull()?.message) }
            onResult(result)
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
                "open_mini_app" -> {
                    val miniApp = resolveMiniApp(action.mini_app_id, action.mini_app_query)
                    if (miniApp == null) {
                        replies += "I could not find that mini app."
                    } else {
                        openMiniApp(miniApp.id)
                        replies += "Opened ${miniApp.name}."
                    }
                }
                "create_mini_app" -> {
                    val prompt = action.mini_app_prompt?.trim().orEmpty()
                    if (prompt.isBlank()) {
                        replies += "I need a description to create a mini app."
                    } else {
                        try {
                            val installed = buildInstallAndMaybeOpenMiniApp(
                                prompt = prompt,
                                openAfterCreate = action.open_after_create != false
                            )
                            replies += "Created ${installed.name}."
                        } catch (error: Exception) {
                            replies += error.message ?: "Could not create that mini app."
                        }
                    }
                }
                "create_mini_app_record" -> {
                    val miniApp = resolveMiniApp(action.mini_app_id, action.mini_app_query)
                    if (miniApp == null) {
                        replies += "I could not find that mini app."
                    } else if (!action.action_id.isNullOrBlank()) {
                        container.miniAppRepository.runAction(miniApp.id, action.action_id)
                        openMiniApp(miniApp.id)
                        replies += "Updated ${miniApp.name}."
                    } else {
                        val recordType = action.record_type ?: "record"
                        container.miniAppRepository.createRecord(miniApp.id, recordType, action.values.orEmpty())
                        openMiniApp(miniApp.id)
                        replies += "Saved that in ${miniApp.name}."
                    }
                }
                "query_mini_app_records" -> {
                    val miniApp = resolveMiniApp(action.mini_app_id, action.mini_app_query)
                    if (miniApp == null) {
                        replies += "I could not find that mini app."
                    } else {
                        val count = container.miniAppRepository.records(miniApp.id).size
                        openMiniApp(miniApp.id)
                        replies += "${miniApp.name} has $count local records."
                    }
                }
            }
        }
        return replies
    }

    private suspend fun buildInstallAndMaybeOpenMiniApp(
        prompt: String,
        openAfterCreate: Boolean
    ): MiniAppInstall {
        val bundle = try {
            container.assistantRepository.buildMiniApp(prompt)
        } catch (_: Exception) {
            localStarterMiniApp(prompt)
        }
        val installed = container.miniAppRepository.install(bundle)
        val installedApps = container.miniAppRepository.listInstalled()
        val records = if (openAfterCreate) container.miniAppRepository.records(bundle.id) else emptyList()
        localState.update {
            it.copy(
                miniApps = installedApps,
                activeMiniApp = if (openAfterCreate) bundle else it.activeMiniApp,
                activeMiniAppRecords = if (openAfterCreate) records else it.activeMiniAppRecords
            )
        }
        return installed
    }

    fun refreshMiniApps() {
        viewModelScope.launch {
            try {
                container.miniAppRepository.ensureBuiltInsInstalled()
                localState.update { it.copy(miniApps = container.miniAppRepository.listInstalled()) }
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message ?: "Could not load mini apps") }
            }
        }
    }

    fun installMiniApp(bundle: MiniAppBundle) {
        viewModelScope.launch {
            try {
                container.miniAppRepository.install(bundle)
                refreshMiniApps()
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message ?: "Could not install mini app") }
            }
        }
    }

    fun createMiniAppFromPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                buildInstallAndMaybeOpenMiniApp(trimmed, openAfterCreate = false)
                return@launch
            } catch (error: Exception) {
                localState.update { it.copy(error = error.message ?: "Could not build mini app; created a local starter instead") }
            }
            installMiniApp(localStarterMiniApp(trimmed))
        }
    }

    private fun localStarterMiniApp(prompt: String): MiniAppBundle {
        val trimmed = prompt.trim()
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "custom" }
        val name = trimmed.split(Regex("\\s+")).take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
        val normalized = trimmed.lowercase()
        val category = when {
            listOf("habit", "health", "water", "workout", "wellness").any { normalized.contains(it) } -> "Wellness"
            listOf("money", "spend", "expense", "budget", "finance").any { normalized.contains(it) } -> "Finance"
            listOf("focus", "task", "plan", "work", "study").any { normalized.contains(it) } -> "Productivity"
            else -> "Custom"
        }
        val color = when (category) {
            "Wellness" -> "#16A34A"
            "Finance" -> "#0F766E"
            "Productivity" -> "#2563EB"
            else -> "#7C3AED"
        }
        return MiniAppBundle(
            id = "local.$slug",
            metadata = com.aura.app.miniapps.MiniAppMetadata(
                name = name,
                description = "A local ${category.lowercase()} app created from: $trimmed",
                category = category
            ),
            theme = com.aura.app.miniapps.MiniAppTheme(primary = color, secondary = "#F59E0B", surface = "#111827"),
            icon = com.aura.app.miniapps.MiniAppIcon(value = name.take(1).uppercase(), background = color),
            dataSchema = com.aura.app.miniapps.MiniAppDataSchema(
                recordType = "entry",
                fields = listOf(
                    com.aura.app.miniapps.MiniAppField("title", "text", required = true),
                    com.aura.app.miniapps.MiniAppField("status", "text", required = true),
                    com.aura.app.miniapps.MiniAppField("note", "text")
                )
            ),
            actions = listOf(
                com.aura.app.miniapps.MiniAppAction("quick_add", "create_record", recordType = "entry", values = mapOf("title" to trimmed, "status" to "Logged")),
                com.aura.app.miniapps.MiniAppAction("mark_priority", "create_record", recordType = "entry", values = mapOf("title" to "Priority", "status" to trimmed)),
                com.aura.app.miniapps.MiniAppAction("save_note", "create_record", recordType = "entry", values = mapOf("title" to "Note", "status" to "Captured"))
            ),
            assistantIntents = listOf(
                com.aura.app.miniapps.MiniAppAssistantIntent("quick_add", listOf("add to $name", "log $name"), actionId = "quick_add"),
                com.aura.app.miniapps.MiniAppAssistantIntent("show_dashboard", listOf("open $name", "show $name"), screenId = "dashboard")
            ),
            screens = listOf(
                com.aura.app.miniapps.MiniAppScreen(
                    id = "dashboard",
                    title = "Dashboard",
                    components = listOf(
                        com.aura.app.miniapps.MiniAppComponent("dashboard_block", "Today", metric = "today_count"),
                        com.aura.app.miniapps.MiniAppComponent("streak_view", "Momentum", metric = "streak"),
                        com.aura.app.miniapps.MiniAppComponent(
                            "quick_action_grid",
                            "Actions",
                            items = listOf(
                                com.aura.app.miniapps.MiniAppComponentItem("Log", "quick_add"),
                                com.aura.app.miniapps.MiniAppComponentItem("Priority", "mark_priority"),
                                com.aura.app.miniapps.MiniAppComponentItem("Note", "save_note")
                            )
                        ),
                        com.aura.app.miniapps.MiniAppComponent("chart", "Last 7 Days", metric = "weekly_count"),
                        com.aura.app.miniapps.MiniAppComponent("timeline", "Activity", source = "records"),
                        com.aura.app.miniapps.MiniAppComponent("slider", "Weekly Pace", metric = "weekly_count")
                    )
                ),
                com.aura.app.miniapps.MiniAppScreen(
                    id = "details",
                    title = "Details",
                    components = listOf(
                        com.aura.app.miniapps.MiniAppComponent(
                            "form",
                            "Custom Entry",
                            items = listOf(com.aura.app.miniapps.MiniAppComponentItem("Save entry"))
                        ),
                        com.aura.app.miniapps.MiniAppComponent(
                            "list",
                            "Shortcuts",
                            items = listOf(
                                com.aura.app.miniapps.MiniAppComponentItem("Log entry", "quick_add", "Capture the default item"),
                                com.aura.app.miniapps.MiniAppComponentItem("Mark priority", "mark_priority", "Pin the most important thing"),
                                com.aura.app.miniapps.MiniAppComponentItem("Save note", "save_note", "Keep a lightweight note")
                            )
                        ),
                        com.aura.app.miniapps.MiniAppComponent("button", "Log now", actionId = "quick_add"),
                        com.aura.app.miniapps.MiniAppComponent(
                            "bottom_sheet",
                            "App note",
                            items = listOf(com.aura.app.miniapps.MiniAppComponentItem("This generated app starts with local capture, history, progress, and assistant actions."))
                        ),
                        com.aura.app.miniapps.MiniAppComponent("settings", "App setup")
                    )
                )
            ),
            capabilities = listOf("local_storage", "assistant_actions")
        )
    }

    fun openMiniApp(id: String) {
        viewModelScope.launch {
            val bundle = container.miniAppRepository.bundle(id)
            val records = container.miniAppRepository.records(id)
            localState.update { it.copy(activeMiniApp = bundle, activeMiniAppRecords = records) }
        }
    }

    fun closeMiniApp() {
        localState.update { it.copy(activeMiniApp = null, activeMiniAppRecords = emptyList()) }
    }

    fun runMiniAppAction(miniAppId: String, actionId: String) {
        viewModelScope.launch {
            container.miniAppRepository.runAction(miniAppId, actionId)
            openMiniApp(miniAppId)
        }
    }

    fun createMiniAppRecord(miniAppId: String, recordType: String, values: Map<String, String>) {
        viewModelScope.launch {
            container.miniAppRepository.createRecord(miniAppId, recordType, values)
            openMiniApp(miniAppId)
        }
    }

    fun deleteMiniAppRecord(miniAppId: String, recordId: String) {
        viewModelScope.launch {
            container.miniAppRepository.deleteRecord(miniAppId, recordId)
            openMiniApp(miniAppId)
        }
    }

    private fun resolveMiniApp(id: String?, query: String?): MiniAppInstall? {
        id?.takeIf { it.isNotBlank() }?.let { miniAppId ->
            uiState.value.miniApps.firstOrNull { it.id == miniAppId }?.let { return it }
        }
        val normalized = query?.trim()?.lowercase().orEmpty()
        if (normalized.isBlank()) return null
        return uiState.value.miniApps.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            ?: uiState.value.miniApps.firstOrNull { it.name.lowercase().contains(normalized) || it.id.lowercase().contains(normalized) }
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

    fun showError(message: String) {
        localState.update { it.copy(error = message) }
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

    fun setIsDefaultLauncher(isDefault: Boolean) {
        localState.update { it.copy(isDefaultLauncher = isDefault) }
        refreshApps(force = false)
    }

    fun setWallpaper(uri: String?) {
        viewModelScope.launch {
            container.sessionStore.setWallpaperUri(uri)
        }
    }

    fun setInteractionMode(mode: String) {
        viewModelScope.launch {
            container.sessionStore.setInteractionMode(mode)
        }
    }

    fun setAppMode(mode: String) {
        viewModelScope.launch {
            container.sessionStore.setAppMode(mode)
        }
    }

    fun setOnboardingComplete(complete: Boolean) {
        viewModelScope.launch {
            container.sessionStore.setOnboardingComplete(complete)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(container) as T
        }
    }
}
