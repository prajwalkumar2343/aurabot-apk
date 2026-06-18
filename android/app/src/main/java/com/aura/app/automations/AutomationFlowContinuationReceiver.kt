package com.aura.app.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.app.AuraApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutomationFlowContinuationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(AlarmAutomationFlowContinuationScheduler.EXTRA_RUN_ID) ?: return
        val retryAttempt = intent.getIntExtra(AlarmAutomationFlowContinuationScheduler.EXTRA_RETRY_ATTEMPT, 0)
            .coerceAtLeast(0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as AuraApplication).container
                AutomationFlowContinuationCoordinator.handle(
                    retryAttempt = retryAttempt,
                    resume = { container.automationEngine.resumeRun(runId) },
                    scheduleRetry = { nextAttempt ->
                        container.automationFlowContinuationScheduler.scheduleRetry(
                            runId = runId,
                            delayMillis = AutomationFlowContinuationCoordinator.RetryDelayMillis,
                            retryAttempt = nextAttempt
                        )
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }
}

internal object AutomationFlowContinuationCoordinator {
    const val RetryDelayMillis = 15_000L
    private const val MaxRetryAttempts = 1

    suspend fun handle(
        retryAttempt: Int,
        resume: suspend () -> Unit,
        scheduleRetry: (Int) -> Unit
    ) {
        try {
            resume()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (retryAttempt < MaxRetryAttempts) {
                runCatching { scheduleRetry(retryAttempt + 1) }
                    .exceptionOrNull()
                    ?.let { error.addSuppressed(it) }
            }
            throw error
        }
    }
}
