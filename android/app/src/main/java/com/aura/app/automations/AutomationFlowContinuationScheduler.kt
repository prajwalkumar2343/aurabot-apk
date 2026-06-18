package com.aura.app.automations

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

interface AutomationFlowContinuationScheduler {
    fun schedule(runId: String, delayMillis: Long)
    fun scheduleRetry(runId: String, delayMillis: Long, retryAttempt: Int) = schedule(runId, delayMillis)
    fun cancel(runId: String)
}

object NoOpAutomationFlowContinuationScheduler : AutomationFlowContinuationScheduler {
    override fun schedule(runId: String, delayMillis: Long) = Unit
    override fun cancel(runId: String) = Unit
}

class AlarmAutomationFlowContinuationScheduler(private val context: Context) : AutomationFlowContinuationScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(runId: String, delayMillis: Long) {
        schedule(runId, delayMillis, retryAttempt = 0)
    }

    override fun scheduleRetry(runId: String, delayMillis: Long, retryAttempt: Int) {
        schedule(runId, delayMillis, retryAttempt.coerceAtLeast(1))
    }

    private fun schedule(runId: String, delayMillis: Long, retryAttempt: Int) {
        val triggerAt = System.currentTimeMillis() + delayMillis.coerceAtLeast(0L)
        cancelLegacyPendingIntent(runId)
        val pendingIntent = createPendingIntent(runId, retryAttempt)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    override fun cancel(runId: String) {
        cancelPendingIntent(existingPendingIntent(runId))
        cancelLegacyPendingIntent(runId)
    }

    private fun createPendingIntent(runId: String, retryAttempt: Int): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(
            context,
            runId.hashCode(),
            intent(runId, retryAttempt, includeIdentity = true),
            flags
        )
    }

    private fun existingPendingIntent(runId: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            runId.hashCode(),
            intent(runId, retryAttempt = 0, includeIdentity = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

    private fun cancelLegacyPendingIntent(runId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            runId.hashCode(),
            intent(runId, retryAttempt = 0, includeIdentity = false),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        cancelPendingIntent(pendingIntent)
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent?) {
        pendingIntent ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun intent(runId: String, retryAttempt: Int, includeIdentity: Boolean): Intent =
        Intent(context, AutomationFlowContinuationReceiver::class.java).apply {
            if (includeIdentity) action = alarmAction(runId)
            putExtra(EXTRA_RUN_ID, runId)
            putExtra(EXTRA_RETRY_ATTEMPT, retryAttempt)
        }

    companion object {
        const val EXTRA_RUN_ID = "automation_run_id"
        const val EXTRA_RETRY_ATTEMPT = "automation_retry_attempt"
        private const val ACTION_PREFIX = "com.aura.app.automation.continuation."

        internal fun alarmAction(runId: String): String = ACTION_PREFIX + runId
    }
}
