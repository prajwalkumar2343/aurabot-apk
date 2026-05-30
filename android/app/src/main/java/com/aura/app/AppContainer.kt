package com.aura.app

import android.content.Context
import com.aura.app.apps.AppBlockStore
import com.aura.app.apps.AppsRepository
import com.aura.app.assistant.AssistantRepository
import com.aura.app.assistant.LlmSettingsStore
import com.aura.app.assistant.LocalAssistantStore
import com.aura.app.session.SessionStore
import com.aura.app.voice.VoiceServiceController

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionStore = SessionStore(appContext)
    val appsRepository = AppsRepository(appContext.packageManager)
    val appBlockStore = AppBlockStore(appContext)
    val llmSettingsStore = LlmSettingsStore(appContext)
    private val localAssistantStore = LocalAssistantStore(appContext)
    val assistantRepository = AssistantRepository(
        baseUrl = BuildConfig.AURA_BACKEND_URL,
        sessionStore = sessionStore,
        localAssistantStore = localAssistantStore,
        llmSettingsStore = llmSettingsStore
    )
    val voiceServiceController = VoiceServiceController(appContext, sessionStore)
}
