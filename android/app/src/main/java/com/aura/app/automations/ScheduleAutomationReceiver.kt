package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
                AutomationBroadcastWork.run(
                    operation = {
                        val container = (context.applicationContext as AuraApplication).container
                        ScheduleAutomationCoordinator.handle(
                            execute = {
                                container.automationEngine.handle(
                                    AutomationEvent(
                                        type = AutomationEvents.ScheduleTick,
                                        automationId = automationId
                                    )
                                )
                            },
                            reschedule = {
                                container.automationRepository.get(automationId)?.takeIf { it.enabled }?.let { spec ->
                                    container.scheduleAutomationScheduler.schedule(spec)
                                }
                            }
                        )
                    },
                    reportFailure = { error ->
                        Log.e(TAG, "Scheduled automation delivery failed for $automationId", error)
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ScheduleAutomation"
    }
}

internal object ScheduleAutomationCoordinator {
    suspend fun handle(execute: suspend () -> Unit, reschedule: suspend () -> Unit) {
        var executionFailure: Throwable? = null
        try {
            execute()
        } catch (error: Exception) {
            executionFailure = error
        }
        var rescheduleFailure: Throwable? = null
        try {
            reschedule()
        } catch (error: Exception) {
            rescheduleFailure = error
        }
        val primaryFailure = executionFailure ?: rescheduleFailure
        if (
            primaryFailure != null &&
            rescheduleFailure != null &&
            primaryFailure !== rescheduleFailure
        ) {
            primaryFailure.addSuppressed(rescheduleFailure)
        }
        primaryFailure?.let { throw it }
    }
}
