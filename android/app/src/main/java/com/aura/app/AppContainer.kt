package com.aura.app

import android.content.Context
import com.aura.app.automations.AndroidAutomationActionExecutor
import com.aura.app.automations.AlarmAutomationFlowContinuationScheduler
import com.aura.app.automations.AutomationDatabase
import com.aura.app.automations.AutomationEngine
import com.aura.app.automations.AutomationRepository
import com.aura.app.automations.AutomationRuntime
import com.aura.app.automations.GeofenceAutomationRegistrar
import com.aura.app.automations.DefaultAutomationContextEnricher
import com.aura.app.automations.LocalDistanceEtaProvider
import com.aura.app.automations.ScheduleAutomationScheduler
import com.aura.app.apps.AppBlockStore
import com.aura.app.apps.AppsRepository
import com.aura.app.assistant.AssistantRepository
import com.aura.app.assistant.LlmSettingsStore
import com.aura.app.assistant.LocalAssistantStore
import com.aura.app.miniapps.MiniAppDatabase
import com.aura.app.miniapps.MiniAppRepository
import com.aura.app.session.SessionStore
import com.aura.app.voice.VoiceServiceController

class AppContainer(context: Context) {
    val appContext = context.applicationContext

    val sessionStore = SessionStore(appContext)
    val appsRepository = AppsRepository(appContext.packageManager)
    val appBlockStore = AppBlockStore(appContext)
    val miniAppRepository = MiniAppRepository(MiniAppDatabase.get(appContext).miniAppDao())
    val automationRepository = AutomationRepository(AutomationDatabase.get(appContext).automationDao())
    val geofenceAutomationRegistrar = GeofenceAutomationRegistrar(appContext)
    val scheduleAutomationScheduler = ScheduleAutomationScheduler(appContext)
    val automationFlowContinuationScheduler = AlarmAutomationFlowContinuationScheduler(appContext)
    val etaProvider = LocalDistanceEtaProvider()
    val automationEngine = AutomationEngine(
        repository = automationRepository,
        contextEnricher = DefaultAutomationContextEnricher(etaProvider),
        actionExecutor = AndroidAutomationActionExecutor(appContext),
        flowContinuationScheduler = automationFlowContinuationScheduler
    )
    val automationRuntime = AutomationRuntime(
        repository = automationRepository,
        geofenceRegistrar = geofenceAutomationRegistrar,
        scheduleScheduler = scheduleAutomationScheduler,
        flowContinuationScheduler = automationFlowContinuationScheduler
    )
    val llmSettingsStore = LlmSettingsStore(appContext)
    private val localAssistantStore = LocalAssistantStore(appContext)
    val assistantRepository = AssistantRepository(
        baseUrl = BuildConfig.AURA_BACKEND_URL,
        sessionStore = sessionStore,
        localAssistantStore = localAssistantStore,
        llmSettingsStore = llmSettingsStore
    )
    val voiceServiceController = VoiceServiceController(appContext, sessionStore)
    val voiceSpeaker = com.aura.app.voice.VoiceSpeaker(appContext)
}
