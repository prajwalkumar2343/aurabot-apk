package com.aura.app.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

interface AutomationFlowContinuationScheduler {
    fun schedule(runId: String, delayMillis: Long)
    fun cancel(runId: String)
}

object NoOpAutomationFlowContinuationScheduler : AutomationFlowContinuationScheduler {
    override fun schedule(runId: String, delayMillis: Long) = Unit
    override fun cancel(runId: String) = Unit
}

class AlarmAutomationFlowContinuationScheduler(private val context: Context) : AutomationFlowContinuationScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(runId: String, delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L)
        val pendingIntent = pendingIntent(runId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    override fun cancel(runId: String) {
        alarmManager.cancel(pendingIntent(runId))
    }

    private fun pendingIntent(runId: String): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(
            context,
            runId.hashCode(),
            Intent(context, AutomationFlowContinuationReceiver::class.java).putExtra(EXTRA_RUN_ID, runId),
            flags
        )
    }

    companion object {
        const val EXTRA_RUN_ID = "automation_run_id"
    }
}
