package com.aura.app.voice

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aura.app.AuraApplication
import com.aura.app.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val canRecord = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        val shouldRestoreListening = canRecord && runBlocking {
            SessionStore(context.applicationContext).state.first().backgroundListeningEnabled
        }
        BootRestoreCoordinator.handle(
            shouldRestoreListening = shouldRestoreListening,
            startListening = { AuraListeningService.start(context.applicationContext) },
            restoreAutomations = {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    runCatching {
                        val app = context.applicationContext as AuraApplication
                        app.container.automationRuntime.restoreTriggers()
                    }
                }
            }
        )
    }
}

internal object BootRestoreCoordinator {
    fun handle(
        shouldRestoreListening: Boolean,
        startListening: () -> Unit,
        restoreAutomations: () -> Unit
    ) {
        if (shouldRestoreListening) {
            startListening()
        }
        restoreAutomations()
    }
}
