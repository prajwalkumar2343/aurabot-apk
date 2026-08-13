package com.aura.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class AuraApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            runCatching { container.assistantRunSurfaceRepository.reconcileStartup() }
        }
        appScope.launch {
            runCatching { container.automationRuntime.restoreTriggers() }
        }
        appScope.launch {
            runCatching { container.dreamRepository.recoverInterruptedApplications() }
            container.dreamSettingsStore.state.collectLatest { settings ->
                runCatching { container.dreamScheduler.reconcile(settings) }
            }
        }
    }
}

val Application.auraContainer: AppContainer
    get() = (this as AuraApplication).container
