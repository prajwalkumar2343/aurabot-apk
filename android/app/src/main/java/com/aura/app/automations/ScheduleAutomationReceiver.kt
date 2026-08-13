package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val automationId = intent.getStringExtra(ScheduleAutomationScheduler.EXTRA_AUTOMATION_ID) ?: return
        if (intent.action != ScheduleAutomationScheduler.alarmAction(automationId)) return
        val persistedTriggerAt = intent.getLongExtra(ScheduleAutomationScheduler.EXTRA_TRIGGER_AT, 0L)
            .takeIf { it > 0L }
        val occurredAt = persistedTriggerAt ?: System.currentTimeMillis()
        AutomationWorkScheduler(context.applicationContext).enqueueEvent(
            deliveryId = "schedule:$automationId:${persistedTriggerAt ?: "legacy"}",
            event = AutomationEvent(
                type = AutomationEvents.ScheduleTick,
                automationId = automationId,
                occurredAt = occurredAt
            ),
            reschedule = true
        )
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
