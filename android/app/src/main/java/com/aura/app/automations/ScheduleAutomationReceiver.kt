package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.app.AuraApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduleAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val automationId = intent.getStringExtra(ScheduleAutomationScheduler.EXTRA_AUTOMATION_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as AuraApplication).container
                container.automationEngine.handle(
                    AutomationEvent(
                        type = AutomationEvents.ScheduleTick,
                        automationId = automationId
                    )
                )
                container.automationRepository.get(automationId)?.takeIf { it.enabled }?.let { spec ->
                    container.scheduleAutomationScheduler.schedule(spec)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
